package com.rfid.message.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rfid.message.entity.Message;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;


public interface MessageMapper extends BaseMapper<Message> {
    /**
     * 获取两个用户之间的所有消息记录（双向查询）
     * @param userId1 用户ID1
     * @param userId2 用户ID2
     * @return 消息列表，按创建时间升序排列
     */
    @Select(" SELECT * FROM message " +
            "        WHERE (user_id = #{userId1} AND target_id = #{userId2})\n" +
            "           OR (user_id = #{userId2} AND target_id = #{userId1})\n" +
            "        ORDER BY create_time ASC\n" +
            "        LIMIT #{offset}, #{limit}")
    public List<Message> getMessages(String userId1, String userId2);


    /**
     * 获取指定用户收到的最新消息（每个发送者只取最新的一条）
     * 使用窗口函数ROW_NUMBER()对每个发送者的消息按时间降序排名，只取排名第一的记录
     * @param userId 目标用户ID
     * @return 消息Map，key为发送者用户ID
     */
    @Select({
            "SELECT * FROM (",
            "    SELECT m.*,",
            "    ROW_NUMBER() OVER (PARTITION BY m.user_id ORDER BY m.create_time DESC) as rn",
            "    FROM message m",
            "    WHERE m.target_id = #{userId}",
            "    AND m.user_id IN (",
            "        SELECT fan_id FROM user_relation",
            "        WHERE user_id = #{userId} AND status = 1",
            "    )",
            ") ranked",
            "WHERE rn = 1",
            "ORDER BY create_time DESC"
    })
    @MapKey("userId")
    public Map<Long, Message> getLatestMessages(String userId);

    /**
     * 获取系统中所有用户的ID列表
     * @return 用户ID列表
     */
    @Select("SELECT id FROM user")
    List<Long> getAllUserIds();

    /**
     * 获取ID大于指定值的用户ID列表
     * @param currentCount 当前查询到的最大ID值
     * @return 新的用户ID列表
     */
    @Select("SELECT id FROM user WHERE id > #{currentCount}")
    List<Long> getNewUserIds(long currentCount);

    /**
     * 查询与指定用户最近有消息交互的用户ID列表
     * @param userId 当前用户ID
     * @param limit 返回数量限制
     * @return 相关用户ID列表
     */
    @Select({
            "SELECT DISTINCT CASE",
            "    WHEN sender_id = #{userId} THEN receiver_id",
            "    ELSE sender_id",
            "END AS related_user_id",
            "FROM message",
            "WHERE sender_id = #{userId} OR receiver_id = #{userId}",
            "ORDER BY create_time DESC",
            "LIMIT #{limit}"
    })
    List<Long> getRelatedUserIds(Long userId,int limit);
}