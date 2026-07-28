package com.careercompass.security.currentuser;

import java.util.UUID;

public interface CurrentUserProvider {
    UUID getCurrentUserId();
}