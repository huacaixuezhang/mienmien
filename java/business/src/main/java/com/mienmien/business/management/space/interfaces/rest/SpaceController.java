package com.mienmien.business.management.space.interfaces.rest;

import com.mienmien.business.management.application.dto.SpaceResponse;
import com.mienmien.business.management.application.service.SpaceApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/spaces")
public class SpaceController {
    private final SpaceApplicationService spaceApplicationService;

    public SpaceController(SpaceApplicationService spaceApplicationService) {
        this.spaceApplicationService = spaceApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse createSpace(@Valid @RequestBody CreateSpaceRequest req) {
        return spaceApplicationService.createSpace(req.name());
    }

    @GetMapping("/{spaceId}")
    public SpaceResponse getSpace(@PathVariable("spaceId") String spaceId) {
        return spaceApplicationService.getSpace(spaceId);
    }

    @GetMapping
    public List<SpaceResponse> listSpaces() {
        return spaceApplicationService.listSpaces();
    }

    @GetMapping("/recycle-bin")
    public List<SpaceResponse> listRecycleBin() {
        return spaceApplicationService.listRecycleBin();
    }

    @PutMapping("/{spaceId}")
    public SpaceResponse renameSpace(@PathVariable("spaceId") String spaceId, @Valid @RequestBody RenameSpaceRequest req) {
        return spaceApplicationService.renameSpace(spaceId, req.name());
    }

    @DeleteMapping("/{spaceId}")
    public SpaceResponse archiveSpace(@PathVariable("spaceId") String spaceId) {
        return spaceApplicationService.archiveSpace(spaceId);
    }

    @PostMapping("/{spaceId}/restore")
    public SpaceResponse restoreSpace(@PathVariable("spaceId") String spaceId) {
        return spaceApplicationService.restoreSpace(spaceId);
    }

    @DeleteMapping("/{spaceId}/hard")
    public SpaceResponse deleteSpace(@PathVariable("spaceId") String spaceId) {
        return spaceApplicationService.deleteSpace(spaceId);
    }

    public record CreateSpaceRequest(@NotBlank String name) {
    }

    public record RenameSpaceRequest(@NotBlank String name) {
    }
}
