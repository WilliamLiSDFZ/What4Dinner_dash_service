package today.what4dinner.what4dinner_dash_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** What the share-link fetcher managed to pull off a post, before any of it reaches the model. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FetchedPost {

    /** Post title with the site suffix stripped. May be null. */
    private String title;

    /** The post body. This is the richest signal — a recipe post usually states the whole recipe. */
    private String text;

    /** Downloaded photo bytes, in post order. Empty when every download failed. */
    private List<FetchedImage> images;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FetchedImage {

        private byte[] bytes;

        /** Always one of the values ImageUploadService accepts. */
        private String contentType;
    }
}
