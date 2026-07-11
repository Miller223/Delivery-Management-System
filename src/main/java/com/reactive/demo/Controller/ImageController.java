package com.reactive.demo.Controller;


import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
@CrossOrigin // Allows your admin React application to connect safely
public class ImageController {

    private final Path rootFolder = Paths.get("uploads");

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> uploadImage(@RequestPart("file") FilePart filePart) {
        
        // 1. Isolate extension (e.g., .jpg, .png) and generate an unrepeatable filename
        String originalName = filePart.filename();
        String extension = originalName.substring(originalName.lastIndexOf("."));
        String uniqueName = UUID.randomUUID().toString() + extension;
        
        Path targetPath = rootFolder.resolve(uniqueName);
        File targetFile = targetPath.toFile();
        																										
        // 2. Stream the binary parts asynchronously to the local folder
        return filePart.transferTo(targetFile)
                .then(Mono.just("http://localhost:8080/images/" + uniqueName)); 
                  // Returns the clean URL string back to your friend's React code
    }
}
