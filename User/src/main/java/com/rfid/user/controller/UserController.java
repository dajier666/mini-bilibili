package com.rfid.user.controller;

import com.rfid.user.entity.DTO.LoginDTO;
import com.rfid.user.entity.RegisterDTO;
import com.rfid.user.entity.Result;
import com.rfid.user.entity.User;
import com.rfid.user.service.impl.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

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

    @GetMapping("/oneUserInfo")
    public Result oneUserInfo(@RequestBody User user) {
        return Result.success(userService.findSingleUser(user));
    }

    @GetMapping("/userListInfo")
    public Result userListInfo(@RequestBody User user) {
        return Result.success(userService.findMultipleUsers(user));
    }

    @PostMapping("/updateUserInfo")
    public Result updateUserInfo(@RequestBody User userInfo) {
        userService.updateUserInfo(userInfo.getId(), userInfo);
        return Result.success();
    }

    @PostMapping("/signIn")
    public Result signIn(@RequestBody User userInfo)  {
        userService.signIn(userInfo.getId());
        return Result.success();
    }
}
