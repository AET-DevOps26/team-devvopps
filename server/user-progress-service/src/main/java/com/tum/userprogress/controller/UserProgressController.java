package com.tum.userprogress.controller;

import com.tum.userprogress.service.UserProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserProgressController {

    private final UserProgressService userProgressService;

    // add endpoints
}