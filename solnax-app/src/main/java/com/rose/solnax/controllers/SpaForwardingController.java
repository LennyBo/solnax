package com.rose.solnax.controllers;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardingController {

    @RequestMapping({
            "/",
            "/{path:^(?!api$|error$|browser$)[^\\.]*}",
            "/{path:^(?!api$|error$|browser$)[^\\.]*}/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}