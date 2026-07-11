package com.reactive.demo.Dto.CustomerApp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MenusResponseDto {
	
	private String itemId;
	private String name;
	private String description;
	private Double price;
	private boolean isAvailable;
	

}
