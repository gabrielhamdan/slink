package com.hamdan.slinkapi.controller;

import com.hamdan.slinkapi.dto.PaginationDto;
import com.hamdan.slinkapi.dto.SlinkDetailDto;
import com.hamdan.slinkapi.dto.SlinkRequestDto;
import com.hamdan.slinkapi.dto.SlinkResponseDto;
import com.hamdan.slinkapi.entity.user.ApiUser;
import com.hamdan.slinkapi.entity.user.User;
import com.hamdan.slinkapi.service.LinkService;
import com.hamdan.slinkapi.service.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@Tag(name = "Links")
public class LinkController {

    private final LinkService linkService;

    private final QrCodeService qrCodeService;

    public LinkController(LinkService linkService, QrCodeService qrCodeService) {
        this.linkService = linkService;
        this.qrCodeService = qrCodeService;
    }

    @GetMapping("/qrcode")
    @Operation(summary = "Este método converte uma URL em QrCode (Base64)")
    public ResponseEntity<String> toQrCode(@RequestParam("link") @Valid @URL String link, @RequestParam(name = "width", required = false) @Min(1) Integer width) {
        return ResponseEntity.ok(qrCodeService.generateQrCode(link, width));
    }

    @PostMapping
    @Operation(summary = "Este método encurta uma URL (Base62 ou link customizado p/ usuários autenticados)")
    public ResponseEntity<SlinkResponseDto> shorten(@AuthenticationPrincipal User user, @RequestBody @Valid SlinkRequestDto req) {
        return ResponseEntity.ok(linkService.shorten(user, req));
    }

    @GetMapping("/{slink}")
    @Operation(summary = "Este método traduz uma URL encurtada")
    public ResponseEntity<Void> resolve(@PathVariable String slink) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, linkService.resolve(slink))
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('API_USER')")
    @Operation(summary = "Este método devolve uma lista com as URLs de um usuário autenticado")
    public ResponseEntity<PaginationDto<SlinkDetailDto>> findAll(@AuthenticationPrincipal ApiUser apiUser, @PageableDefault(sort = {"id"}) Pageable pageable) {
        return ResponseEntity.ok(linkService.findAll(apiUser, pageable));
    }

}
