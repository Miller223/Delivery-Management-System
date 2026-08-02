package com.reactive.demo.Dto.CustomerApp;



import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequestDto {
	@Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters")
    private String name;

    @URL(message = "Image must be a valid URL format")
    private String image;

    // Standard regex for a Myanmar phone number (e.g., 09xxxxxxxxx)
    @Pattern(regexp = "^09\\d{7,9}$", message = "Phone must be a valid Myanmar number starting with 09")
    private String phone;
}
