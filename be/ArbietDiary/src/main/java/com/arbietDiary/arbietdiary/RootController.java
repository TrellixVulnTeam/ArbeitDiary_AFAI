package com.arbietDiary.arbietdiary;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController implements ErrorController{
	@GetMapping("/error")
	public String redirectRoot() {
		return "../public/index.html";
	}
	
//	@GetMapping("/index.html")
//	public String index() {
//		return "../public/index";
//	}
}
