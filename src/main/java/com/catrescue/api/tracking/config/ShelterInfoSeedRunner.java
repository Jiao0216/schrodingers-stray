package com.catrescue.api.tracking.config;

import com.catrescue.api.tracking.persistence.ShelterInfoEntity;
import com.catrescue.api.tracking.repository.ShelterInfoJpaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds Bay Area shelter / TNR directory rows when {@code shelter_info} is empty.
 */
@Component
@Order(40)
public class ShelterInfoSeedRunner implements ApplicationRunner {

    private final ShelterInfoJpaRepository shelterInfoJpaRepository;

    public ShelterInfoSeedRunner(ShelterInfoJpaRepository shelterInfoJpaRepository) {
        this.shelterInfoJpaRepository = shelterInfoJpaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (shelterInfoJpaRepository.count() > 0) {
            return;
        }
        shelterInfoJpaRepository.saveAll(java.util.List.of(
                row(
                        "City of San Jose Animal Services",
                        "2750 Monterey Rd, San Jose, CA 95111",
                        37.3382,
                        -121.8863,
                        "+1-408-794-7297",
                        "TNR,紧急救助,领养",
                        "Municipal shelter — ask about community cat TNR programs."
                ),
                row(
                        "Humane Society Silicon Valley",
                        "901 Ames Ave, Milpitas, CA 95035",
                        37.4283,
                        -121.9067,
                        "+1-408-262-2133",
                        "TNR,绝育,领养",
                        "South Bay TNR and clinic services — confirm feral/community cat intake."
                ),
                row(
                        "Town Cats (TNR support)",
                        "PO Box 70256, San Jose, CA 95170",
                        37.3541,
                        -121.9552,
                        "",
                        "TNR,绝育",
                        "Volunteer-run TNR group serving Santa Clara County — contact before trapping."
                ),
                row(
                        "Nine Lives Foundation",
                        "3016 Rolison Rd, Redwood City, CA 94063",
                        37.4789,
                        -122.1930,
                        "+1-650-368-2985",
                        "TNR,紧急救助,领养",
                        "Cat-focused rescue on the Peninsula — verify current TNR voucher programs."
                )
        ));
    }

    private static ShelterInfoEntity row(
            String name,
            String address,
            double lat,
            double lng,
            String phone,
            String services,
            String notes
    ) {
        ShelterInfoEntity e = new ShelterInfoEntity();
        e.setName(name);
        e.setAddress(address);
        e.setLatitude(lat);
        e.setLongitude(lng);
        e.setPhone(phone);
        e.setServices(services);
        e.setNotes(notes);
        return e;
    }
}
