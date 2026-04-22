package me.jcloud.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HelloController {

    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "JCloud structure is working";
    }
}
