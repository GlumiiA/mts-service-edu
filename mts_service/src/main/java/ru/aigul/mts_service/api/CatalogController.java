package ru.aigul.mts_service.api;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import ru.aigul.mts_service.dto.CursorPage;
import ru.aigul.mts_service.dto.catalog.*;
import ru.aigul.mts_service.search.TariffDocument;
import ru.aigul.mts_service.search.TariffSearchService;
import ru.aigul.mts_service.search.SearchResult;
import ru.aigul.mts_service.service.CatalogService;

import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CATALOG_READ')")
public class CatalogController {

    private final CatalogService catalogService;
    private final TariffSearchService tariffSearchService;

    @Value("${app.pagination.default-limit}")
    private int defaultLimit;

    @GetMapping("/home")
    public CursorPage<TariffHomeCardDto> getHome(
            @RequestParam Long cityId,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return catalogService.getHome(cityId, priceMax, after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/mobile")
    public CursorPage<TariffMobileCardDto> getMobile(
            @RequestParam(required = false) Integer internetGb,
            @RequestParam(required = false) Integer callsMinutes,
            @RequestParam(required = false) Boolean forFamily,
            @RequestParam(required = false) Boolean noSubscriptionFee,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return catalogService.getMobile(internetGb, callsMinutes, forFamily, noSubscriptionFee, after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/devices")
    public CursorPage<TariffDeviceCardDto> getDevices(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return catalogService.getDevices(after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/landline")
    public CursorPage<TariffLandlineCardDto> getLandline(
            @RequestParam Long cityId,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return catalogService.getLandline(cityId, after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/satellite-tv")
    public CursorPage<TariffSatelliteCardDto> getSatelliteTv(
            @RequestParam Long cityId,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return catalogService.getSatelliteTv(cityId, after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/business")
    public CursorPage<TariffBusinessCardDto> getBusiness(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return catalogService.getBusiness(after, limit != null ? limit : defaultLimit);
    }

    @GetMapping("/tariffs/{tariffId}")
    public TariffDetailDto getTariff(@PathVariable Long tariffId,
                                     @RequestParam(required = false) Long cityId) {
        return catalogService.getTariff(tariffId, cityId);
    }

    @GetMapping("/cities")
    public List<CityDto> getCities() {
        return catalogService.getCities();
    }

    @GetMapping("/services")
    public List<ServiceDto> getServices() {
        return catalogService.getServices();
    }

    @GetMapping("/search")
    public CursorPage<TariffDocument> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal priceMax,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit,
            HttpServletResponse response) {
        int lim = limit != null ? limit : defaultLimit;
        Double price = priceMax != null ? priceMax.doubleValue() : null;
        SearchResult<TariffDocument> res = tariffSearchService.search(q, category, price, after, lim);
        if (res != null && res.getSource() != null) response.setHeader("X-Search-Source", res.getSource());
        return res != null && res.getPage() != null ? res.getPage() : new CursorPage<>(List.of(), null);
    }
}