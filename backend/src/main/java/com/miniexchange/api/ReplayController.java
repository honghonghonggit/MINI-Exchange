package com.miniexchange.api;

import com.miniexchange.api.dto.ReplayResult;
import com.miniexchange.service.ReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReplayController {

    private final ReplayService replayService;

    /** 기록된 이벤트를 처음부터 재생해 체결 과정을 재구성한다. */
    @GetMapping("/replay")
    public ResponseEntity<ReplayResult> replay() {
        return ResponseEntity.ok(replayService.replay());
    }
}
