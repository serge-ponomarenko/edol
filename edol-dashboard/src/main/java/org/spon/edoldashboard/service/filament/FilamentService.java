package org.spon.edoldashboard.service.filament;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spon.edoldashboard.model.entity.Filament;
import org.spon.edoldashboard.model.entity.MaterialType;
import org.spon.edoldashboard.model.entity.Vendor;
import org.spon.edoldashboard.repository.FilamentRepository;
import org.spon.edoldashboard.repository.MaterialTypeRepository;
import org.spon.edoldashboard.repository.VendorRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class FilamentService {

    private final FilamentRepository filamentRepository;
    private final MaterialTypeRepository materialTypeRepository;
    private final VendorRepository vendorRepository;

    @Transactional
    @CacheEvict(value = "spools", allEntries = true)
    public Filament findOrCreateFilament(String fullId, String color, String filamentBrandIndex) {
        Optional<Filament> existing =
                filamentRepository.findFirstByFullIdAndColorHex(fullId, color);

        if (existing.isPresent()) {
            Filament filament = existing.get();
            filament.setPrinterFilamentProfileId(filamentBrandIndex);  // Update filamentBrandIndex (it may be changed in Bambu studio)
            filamentRepository.save(filament);
            return existing.get();
        }

        String[] parts = fullId.split(" ");

        MaterialType material = null;
        int materialIndex = -1;

        for (int i = 0; i < parts.length; i++) {

            Optional<MaterialType> found =
                    materialTypeRepository.findByNameIgnoreCase(parts[i]);

            if (found.isPresent()) {
                material = found.get();
                materialIndex = i;
                break;
            }
        }

        if (material == null) {
            throw new IllegalArgumentException("Cannot detect material from fullId: " + fullId);
        }

        String vendorName =
                materialIndex == 0
                        ? "Unknown"
                        : String.join(" ", Arrays.copyOfRange(parts, 0, materialIndex));

        String brand =
                materialIndex + 1 < parts.length
                        ? String.join(" ", Arrays.copyOfRange(parts, materialIndex + 1, parts.length))
                        : material.getName();

        Vendor vendor = vendorRepository
                .findByNameIgnoreCase(vendorName)
                .orElseGet(() ->
                        vendorRepository.save(
                                Vendor.builder()
                                        .name(vendorName)
                                        .build()
                        )
                );

        Filament filament = new Filament();

        filament.setFullId(fullId);
        filament.setBrand(brand);
        filament.setColorHex(color);

        filament.setPrinterFilamentProfileId(filamentBrandIndex);

        filament.setVendor(vendor);
        filament.setMaterialType(material);
        filament.setDiameter(1.75);

        log.info("+ New Filament has been created. Full ID: {}, Color: {}",
                filament.getFullId(),
                filament.getColorHex());

        return filamentRepository.save(filament);
    }

}
