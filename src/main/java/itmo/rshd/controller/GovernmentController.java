package itmo.rshd.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import itmo.rshd.service.RegionAssessmentService;

@RestController
@RequestMapping("/api/government")
public class GovernmentController {
    
    @Autowired
    private RegionAssessmentService regionAssessmentService;
    
    @PostMapping("/assess-region/{region}")
    public boolean assessRegion(@PathVariable String region) {
        return regionAssessmentService.shouldDeployOreshnik(region);
    }
    
    @PostMapping("/deploy-oreshnik/{region}")
    public void deployOreshnik(@PathVariable String region) {
        regionAssessmentService.deployOreshnik(region);
    }
}
