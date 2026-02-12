// package com.juvis.juvis._core.web;

// import org.springframework.stereotype.Controller;
// import org.springframework.web.bind.annotation.RequestMapping;

// @Controller
// public class SpaForwardController {

//     /**
//      *  - /api/**, /actuator/**, /docs/** 는 제외
//      *  - 확장자(.)가 있는 정적 리소스 요청은 제외
//      *  - 나머지는 전부 index.html로 forward (Flutter Web SPA)
//      */
//     @RequestMapping(value = {
//             "/{path:^(?!api|actuator|docs$).*$}",
//             "/**/{path:^(?!api|actuator|docs$).*$}"
//     })
//     public String forward() {
//         return "forward:/index.html";
//     }
// }
