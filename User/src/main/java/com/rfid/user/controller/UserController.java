package com.rfid.user.controller;

import com.rfid.user.entity.DTO.LoginDTO;
import com.rfid.user.entity.DTO.LoginResultDTO;
import com.rfid.user.entity.RegisterDTO;
import com.rfid.user.entity.Result;
import com.rfid.user.entity.User;
import com.rfid.user.service.impl.UserMapperServiceImpl;
import com.rfid.user.service.impl.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapperServiceImpl userMapperService;
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result register(@RequestBody RegisterDTO registerDTO) {
        boolean success = userService.register(registerDTO);
        return Result.success(success);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO) {
        LoginResultDTO result = userService.login(loginDTO);
        return Result.success(result);
    }

    /**
     * 关注用户
     */
    @PostMapping("/follow")
    public Result followUser(
            @RequestParam Long userId,
            @RequestParam Long targetId) {
        userService.followUser(userId, targetId);
        return Result.success();
    }

    /**
     * 取消关注
     */
    @DeleteMapping("/follow/{targetId}")
    public Result unfollowUser(
            @RequestParam Long userId,
            @PathVariable Long targetId) {
        userService.unfollowUser(userId, targetId);
        return Result.success();
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/{userId}")
    public Result getUserInfo(@PathVariable Long userId) {
        User user = userMapperService.getById(userId);
        return Result.success(user);
    }
}
