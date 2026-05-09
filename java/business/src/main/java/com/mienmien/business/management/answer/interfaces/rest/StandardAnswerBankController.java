package com.mienmien.business.management.answer.interfaces.rest;

import com.mienmien.business.management.application.dto.StandardAnswerBankResponse;
import com.mienmien.business.management.application.service.StandardAnswerBankApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/answer-banks")
public class StandardAnswerBankController {
    private final StandardAnswerBankApplicationService standardAnswerBankApplicationService;

    public StandardAnswerBankController(StandardAnswerBankApplicationService standardAnswerBankApplicationService) {
        this.standardAnswerBankApplicationService = standardAnswerBankApplicationService;
    }

    @GetMapping("/{spaceId}")
    public StandardAnswerBankResponse getBySpace(@PathVariable("spaceId") String spaceId) {
        return standardAnswerBankApplicationService.getBySpace(spaceId);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public StandardAnswerBankResponse upsert(@Valid @RequestBody UpsertStandardAnswerBankRequest req) {
        return standardAnswerBankApplicationService.upsert(
                req.spaceId(),
                req.intro(),
                req.reason(),
                req.strengths(),
                req.project(),
                req.hr(),
                req.cardsJson()
        );
    }

    public record UpsertStandardAnswerBankRequest(
            @NotBlank String spaceId,
            String intro,
            String reason,
            String strengths,
            String project,
            String hr,
            String cardsJson) {
    }
}
