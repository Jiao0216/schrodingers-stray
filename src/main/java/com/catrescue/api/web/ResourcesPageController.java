package com.catrescue.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Bookmark-friendly redirect to the rescue/TNR directory detail static page.
 */
@Controller
public class ResourcesPageController {

    @GetMapping("/resources/rescue-detail")
    public String rescueDetail() {
        return "redirect:/resources/rescue-detail.html";
    }
}
