package com.rfid.user.service.impl;


import com.rfid.user.entity.DTO.LoginDTO;
import com.rfid.user.entity.DTO.LoginResultDTO;
import com.rfid.user.entity.RegisterDTO;
import com.rfid.user.exception.BusinessException;
import com.rfid.user.utils.JwtTokenUtil;
import com.rfid.user.utils.SnowflakeIdGenerator;
import org.redisson.api.RSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.rfid.user.entity.User;
import com.rfid.user.entity.UserRelation;
import com.rfid.user.entity.UserPoints;
import com.rfid.user.enums.UserStatus;
import com.rfid.user.enums.RelationType;
import org.springframework.beans.BeanUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserRelationMapperServiceImpl userRelationMapperServiceImpl;

    @Autowired
    private UserPointsMapperServiceImpl userPointsMapperServiceImpl;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private UserMapperServiceImpl userMapperServiceImpl;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    //排行榜配置
    private static final String LEVEL_RANK_KEY = "user:level_rank";

    //签到配置
    private static final String SIGN_IN_KEY_PREFIX = "user:sign_in:";
    private static final int POINTS_PER_SIGN_IN = 1;
    private static final int EXP_PER_SIGN_IN = 5;


    


    public boolean register(RegisterDTO registerDTO) {
        // 检查用户名/邮箱/手机号是否已存在
        checkUserExists(registerDTO.getEmail(), registerDTO.getPhone());

        // 密码加密
        String encryptedPassword = passwordEncoder.encode(registerDTO.getPassword());

        // 创建用户
        User user = new User();
        BeanUtils.copyProperties(registerDTO, user);
        user.setId(snowflakeIdGenerator.nextId());
        user.setPassword(encryptedPassword);
        user.setStatus(UserStatus.NORMAL.getCode());
        user.setCreateTime(LocalDateTime.now());
        userMapperServiceImpl.save(user);

        // 初始化用户积分
        UserPoints userPoints = new UserPoints();
        userPoints.setUserId(user.getId());
        userPoints.setPoints(0); // 初始积分
        userPoints.setLevel(1);    // 初始等级
        userPoints.setExp(0);
        userPoints.setLastUpdateTime(LocalDateTime.now());
        userPointsMapperServiceImpl.save(userPoints);

        return true;
    }

    public void checkUserExists(String phone, String email) {
        User user = userMapperServiceImpl.getBaseMapper().selectByPhoneOrEmail(phone, email);
        if (user != null) {
            throw new BusinessException("该电话/邮箱已被注册");
        }
    }

    public void checkUserExists(Long userId) {
        User user = userMapperServiceImpl.getById(userId);
        if (user != null) {
            throw new BusinessException("用户不存在");
        }
    }


    public LoginResultDTO login(LoginDTO loginDTO) {
        // 根据电话或者邮箱查找用户
        User user = userMapperServiceImpl.getBaseMapper().selectByPhoneOrEmail(loginDTO.getPhone(), loginDTO.getEmail());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 校验密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 生成JWT Token
        String token = jwtTokenUtil.doGenerateToken(new HashMap<>(), user.getId().toString());

        // 返回登录结果
        LoginResultDTO result = new LoginResultDTO();
        result.setUserID(user.getId());
        result.setUsername(user.getUsername());
        result.setToken(token);
        result.setExpireTime(jwtTokenUtil.getExpirationDateFromToken(token));

        return result;
    }


    public void followUser(Long userId, Long targetId) {
        // 检查用户是否存在
        checkUserExists(userId);
        checkUserExists(targetId);

        // 检查是否已关注
        UserRelation relation = userRelationMapperServiceImpl.getBaseMapper().selectByUserIdAndTargetId(userId, targetId);
        if (relation != null) {
            if (relation.getType() == RelationType.FOLLOW.getCode()) {
                throw new BusinessException("已关注该用户");
            } else {
                // 更新关系类型
                relation.setType(RelationType.FOLLOW.getCode());
                userRelationMapperServiceImpl.updateById(relation);

                //发送关注消息
                kafkaProducerService.sendFollowMessage(userId, targetId);
            }
        } else {
            // 创建关注关系
            UserRelation newRelation = new UserRelation();
            newRelation.setUserId(userId);
            newRelation.setTargetId(targetId);
            newRelation.setType(RelationType.FOLLOW.getCode());
            newRelation.setCreateTime(LocalDateTime.now());
            userRelationMapperServiceImpl.save(newRelation);

            // 创建粉丝关系
            UserRelation fanRelation = new UserRelation();
            fanRelation.setUserId(targetId);
            fanRelation.setTargetId(userId);
            fanRelation.setType(RelationType.FAN.getCode());
            fanRelation.setCreateTime(LocalDateTime.now());
            userRelationMapperServiceImpl.save(fanRelation);

            //发送关注消息
            kafkaProducerService.sendFollowMessage(userId, targetId);
        }
    }

    /**
     * 取消关注用户
     * @param userId 用户ID
     * @param targetId 目标用户ID
     */
    public void unfollowUser(Long userId, Long targetId) {
        // 检查用户是否存在
        checkUserExists(userId);
        checkUserExists(targetId);

        // 获取关注关系
        UserRelation followRelation = userRelationMapperServiceImpl.getBaseMapper().selectByUserIdAndTargetId(userId, targetId);
        if (followRelation == null || followRelation.getType() != RelationType.FOLLOW.getCode()) {
            throw new BusinessException("未关注该用户");
        }

        // 删除关注关系
        userRelationMapperServiceImpl.removeById(followRelation.getId());

        // 获取并删除粉丝关系
        UserRelation fanRelation = userRelationMapperServiceImpl.getBaseMapper().selectByUserIdAndTargetId(targetId, userId);
        if (fanRelation != null && fanRelation.getType() == RelationType.FAN.getCode()) {
            userRelationMapperServiceImpl.removeById(fanRelation.getId());
        }
    }

    /**
     * 将用户加入黑名单
     * @param userId 用户ID
     * @param targetId 目标用户ID
     */
    public void addToBlacklist(Long userId, Long targetId) {
        // 检查用户是否存在
        checkUserExists(userId);
        checkUserExists(targetId);

        // 检查是否已在黑名单
        UserRelation relation = userRelationMapperServiceImpl.getBaseMapper().selectByUserIdAndTargetId(userId, targetId);
        if (relation != null && relation.getType() == RelationType.BLACKLIST.getCode()) {
            throw new BusinessException("该用户已在黑名单中");
        }

        if (relation == null) {
            // 创建黑名单关系
            UserRelation newRelation = new UserRelation();
            newRelation.setUserId(userId);
            newRelation.setTargetId(targetId);
            newRelation.setType(RelationType.BLACKLIST.getCode());
            newRelation.setCreateTime(LocalDateTime.now());
            userRelationMapperServiceImpl.save(newRelation);
        } else {
            // 更新为黑名单关系
            relation.setType(RelationType.BLACKLIST.getCode());
            userRelationMapperServiceImpl.updateById(relation);
        }
    }
    
    /**
     * 扣减用户积分
     * @param userId 用户ID
     * @param points 要扣减的积分数量
     */
    public void deductPoints(Long userId, int points) {
        if (points < 0) {
            throw new BusinessException("扣减积分数量不能为负数");
        }
        UserPoints userPoints = userPointsMapperServiceImpl.getBaseMapper().selectByUserId(userId);
        if (userPoints == null) {
            throw new BusinessException("用户积分信息不存在");
        }
        if (userPoints.getPoints() < points) {
            throw new BusinessException("用户积分不足");
        }
        userPoints.setPoints(userPoints.getPoints() - points);
        userPoints.setLastUpdateTime(LocalDateTime.now());
        userPointsMapperServiceImpl.updateById(userPoints);
    }

    /**
     * 增加用户积分
     * @param userId 用户ID
     * @param points 要增加的积分数量
     */
    public void addPoints(Long userId, int points) {
        if (points < 0) {
            throw new BusinessException("增加积分数量不能为负数");
        }
        UserPoints userPoints = userPointsMapperServiceImpl.getBaseMapper().selectByUserId(userId);
        if (userPoints == null) {
            throw new BusinessException("用户积分信息不存在");
        }
        userPoints.setPoints(userPoints.getPoints() + points);
        userPoints.setLastUpdateTime(LocalDateTime.now());
        userPointsMapperServiceImpl.updateById(userPoints);
    }

    /**
     * 增加用户经验
     * @param userId 用户ID
     * @param exp 要增加的经验值
     */
    public void addExp(Long userId, int exp) {
        if (exp < 0) {
            throw new BusinessException("增加经验值不能为负数");
        }
        UserPoints userPoints = userPointsMapperServiceImpl.getBaseMapper().selectByUserId(userId);
        if (userPoints == null) {
            throw new BusinessException("用户积分信息不存在");
        }
        userPoints.setExp(userPoints.getExp() + exp);
        
        // 处理等级升级逻辑
        while (userPoints.getExp() >= 100) {
            userPoints.setLevel(userPoints.getLevel() + 1);
            userPoints.setExp(userPoints.getExp() - 100);
        }
        
        userPoints.setLastUpdateTime(LocalDateTime.now());
        userPointsMapperServiceImpl.updateById(userPoints);
    }
    
    /**
     * 修改用户个人信息
     * @param userId 用户ID
     * @param userInfo 包含新个人信息的用户对象
     */
    public void updateUserInfo(Long userId, User userInfo) {
        if (userId == null || userInfo == null) {
            throw new BusinessException("用户ID和用户信息不能为null");
        }
        
        User user = userMapperServiceImpl.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 更新用户信息，仅更新非空字段
        if (userInfo.getUsername() != null) {
            user.setUsername(userInfo.getUsername());
        }
        if (userInfo.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(userInfo.getPassword()));
        }
        if (userInfo.getEmail() != null) {
            // 检查邮箱是否已被使用
            User emailUser = userMapperServiceImpl.getBaseMapper().selectByPhoneOrEmail(null, userInfo.getEmail());
            if (emailUser != null && !emailUser.getId().equals(userId)) {
                throw new BusinessException("该邮箱已被注册");
            }
            user.setEmail(userInfo.getEmail());
        }
        if (userInfo.getPhone() != null) {
            // 检查手机号是否已被使用
            User phoneUser = userMapperServiceImpl.getBaseMapper().selectByPhoneOrEmail(userInfo.getPhone(), null);
            if (phoneUser != null && !phoneUser.getId().equals(userId)) {
                throw new BusinessException("该手机号已被注册");
            }
            user.setPhone(userInfo.getPhone());
        }
        if (userInfo.getGender() != null) {
            user.setGender(userInfo.getGender());
        }
        if (userInfo.getBirthday() != null) {
            user.setBirthday(userInfo.getBirthday());
        }
        if (userInfo.getAvatar() != null) {
            user.setAvatar(userInfo.getAvatar());
        }
        if (userInfo.getSignature() != null) {
            user.setSignature(userInfo.getSignature());
        }
        
        user.setUpdateTime(LocalDateTime.now());
        userMapperServiceImpl.updateById(user);
    }

    /**
     * 用户签到
     * @param userId 用户ID
     * @return 是否签到成功
     */
    public boolean signIn(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为null");
        }
        
        LocalDate today = LocalDate.now();
        String signInKey = SIGN_IN_KEY_PREFIX + userId + ":" + today;
        
        // 获取 RValue 实例
        var rValue = redissonClient.getBucket(signInKey);
        
        // 检查是否已签到
        if (rValue.isExists()) {
            throw new BusinessException("今日已签到，请勿重复签到");
        }
        
        // 记录签到信息
        rValue.set("1");
        
        // 设置过期时间为当天结束
        LocalDateTime endOfDay = LocalDateTime.of(today, LocalTime.MAX);
        long expireSeconds = java.time.Duration.between(LocalDateTime.now(), endOfDay).getSeconds();
        rValue.expire(expireSeconds, TimeUnit.SECONDS);

        // 发放签到奖励
        addPoints(userId, POINTS_PER_SIGN_IN);
        addExp(userId, EXP_PER_SIGN_IN);

        return true;
    }

    /**
     * 更新用户等级到排行榜
     * @param userId 用户ID
     * @param level 用户等级
     */
//    public void updateUserLevelRank(Long userId, Integer level) {
//        RSortedSet<Long> levelRankSet = redissonClient.getSortedSet(LEVEL_RANK_KEY);
//        levelRankSet.addScore(userId, level.doubleValue());
//    }

    /**
     * 获取等级排行榜前 N 名用户
     * @param topN 前 N 名
     * @return 用户ID列表，按等级从高到低排序
     */
//    public List<Long> getTopNLevelRank(int topN) {
//        RSortedSet<Long> levelRankSet = redissonClient.getSortedSet(LEVEL_RANK_KEY);
//        return levelRankSet.entryRangeReversed(0, topN - 1).stream()
//                .map(entry -> entry.getValue())
//                .collect(Collectors.toList());
//    }
}
