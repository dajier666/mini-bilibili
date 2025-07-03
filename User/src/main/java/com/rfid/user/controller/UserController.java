package com.rfid.user.controller;

import com.rfid.user.entity.DTO.LoginDTO;
import com.rfid.user.entity.RegisterDTO;
import com.rfid.user.entity.Result;
import com.rfid.user.service.impl.UserMapperServiceImpl;
import com.rfid.user.service.impl.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private UserMapperServiceImpl userMapperServiceImpl;

    @PostMapping("/login")
    public Result userLogin(@RequestBody LoginDTO loginDTO) {
        return Result.success(userService.login(loginDTO));
    }
    @PostMapping("/register")
    public Result userRegister(@RequestBody RegisterDTO registerDTO) {
        return Result.success(userService.register(registerDTO));
    }
    @PostMapping("/follow")
    public Result userFollow(@RequestBody Long userId, @RequestBody Long targetId) {
        userService.followUser(userId, targetId);
        return Result.success();
    }
    @PostMapping("/unfollow")
    public Result userUnfollow(@RequestBody Long userId, @RequestBody Long targetId) {
        userService.unfollowUser(userId, targetId);
        return Result.success();
    }
    @PostMapping("/blacklist")
    public Result userBlacklist(@RequestBody Long userId, @RequestBody Long targetId) {
        userService.addToBlacklist(userId, targetId);
        return Result.success();
    }
    @GetMapping("/userInfo/{userId}")
    public Result userInfo(@PathVariable Long userId) {
        return Result.success(userMapperServiceImpl.getById(userId));
    }
}
