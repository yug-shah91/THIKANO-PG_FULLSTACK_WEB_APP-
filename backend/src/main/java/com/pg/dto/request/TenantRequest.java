package com.pg.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class TenantRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank @Email(message = "Valid email required")
    private String email;

    @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password; // Owner sets initial password for tenant

    private String phone;

    // Which room to assign them to (optional — can allocate later)
    private Long roomId;

    private LocalDate joinDate;
    private LocalDate expectedLeaveDate;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String idProofType;
    private String idProofNumber;
}
