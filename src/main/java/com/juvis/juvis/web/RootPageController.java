package com.juvis.juvis.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Profile("dev")
@Controller
public class RootPageController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String root() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>아이디진정성</title>
                  <style>
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI",
                                   Roboto, Helvetica, Arial, sans-serif;
                      background: #f7f7f7;
                      padding: 40px;
                    }
                    .box {
                      max-width: 720px;
                      margin: 0 auto;
                      background: #ffffff;
                      padding: 32px;
                      border-radius: 16px;
                      box-shadow: 0 10px 30px rgba(0,0,0,0.08);
                    }
                    h1 {
                      margin-top: 0;
                    }
                    ul {
                      margin-top: 16px;
                    }
                    .hint {
                      margin-top: 24px;
                      color: #666;
                      font-size: 14px;
                    }
                  </style>
                </head>
                <body>
                  <div class="box">
                    <h1>아이디진정성 서버 정상 동작 중 ✅</h1>
                    <p>
                      현재 서비스는 <strong>API 서버</strong>로 정상 운영 중입니다.
                    </p>
                    <ul>
                      <li><code>/api/**</code> : 모바일 / 웹 앱 API</li>
                      <li><code>/actuator/health</code> : 서버 헬스 체크</li>
                    </ul>
                    <div class="hint">
                      이 페이지는 임시 상태 페이지이며,<br/>
                      프론트엔드(Web) 배포 시 자동으로 대체됩니다.
                    </div>
                  </div>
                </body>
                </html>
                """;
    }
}
