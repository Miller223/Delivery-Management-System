package com.reactive.demo.Model;


import lombok.Data;

@Data
public  class MenuItem {
    private String itemId;
    private String name;
    private String description;
    private String image;
    private Double price;
    private boolean isAvailable;
}
