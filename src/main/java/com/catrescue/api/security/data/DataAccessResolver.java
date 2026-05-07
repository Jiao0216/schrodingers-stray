package com.catrescue.api.security.data;

import com.catrescue.api.config.DataAccessProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link DataAccessTier} from optional headers (no session assumed).
 */
@Component
public class DataAccessResolver {

    public static final String HEADER_ADMIN_DATA = "X-Cat-Rescue-Admin-Data-Token";
    public static final String HEADER_B_DATA = "X-Cat-Rescue-B-Data-Token";

    private final DataAccessProperties properties;

    public DataAccessResolver(DataAccessProperties properties) {
        this.properties = properties;
    }

    public DataAccessTier resolve(HttpServletRequest request) {
        if (request == null) {
            return DataAccessTier.PUBLIC;
        }
        String admin = trim(properties.getAdminDataToken());
        String inst = trim(properties.getBDataToken());
        String hAdmin = trim(request.getHeader(HEADER_ADMIN_DATA));
        String hInst = trim(request.getHeader(HEADER_B_DATA));
        if (!admin.isEmpty() && admin.equals(hAdmin)) {
            return DataAccessTier.ADMIN;
        }
        if (!inst.isEmpty() && inst.equals(hInst)) {
            return DataAccessTier.INSTITUTION;
        }
        return DataAccessTier.PUBLIC;
    }

    public boolean hasPreciseGeo(DataAccessTier tier) {
        return tier == DataAccessTier.ADMIN || tier == DataAccessTier.INSTITUTION;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
