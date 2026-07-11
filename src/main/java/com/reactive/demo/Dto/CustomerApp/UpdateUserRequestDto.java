package com.reactive.demo.Dto.CustomerApp;



import org.hibernate.validator.constraints.URL;

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

    
    private String phone;
}
