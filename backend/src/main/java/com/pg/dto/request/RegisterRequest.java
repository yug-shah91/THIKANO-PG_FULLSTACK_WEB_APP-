package com.pg.dto.request;

import com.pg.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank @Email(message = "Valid email required")
    private String email;

    @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;

    private String phone;

    // Only OWNER is accepted on public registration (enforced in AuthService).
    private Role role = Role.OWNER;
}
