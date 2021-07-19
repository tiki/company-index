package com.mytiki.company_index.features.latest.company;

import com.mytiki.common.exception.ApiExceptionBuilder;
import com.mytiki.common.reply.ApiReplyAOMessageBuilder;
import com.mytiki.company_index.features.latest.big_picture.BigPictureAO;
import com.mytiki.company_index.features.latest.big_picture.BigPictureException;
import com.mytiki.company_index.features.latest.big_picture.BigPictureService;
import com.mytiki.company_index.features.latest.hibp.HibpService;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

public class CompanyService {
    private final CompanyRepository companyRepository;
    private final BigPictureService bigPictureService;
    private final HibpService hibpService;

    public CompanyService(
            CompanyRepository companyRepository,
            BigPictureService bigPictureService,
            HibpService hibpService) {
        this.companyRepository = companyRepository;
        this.bigPictureService = bigPictureService;
        this.hibpService = hibpService;
    }

    public CompanyAO findByDomain(String domain) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Optional<CompanyDO> companyOptional = companyRepository.findByDomain(domain);
        if (companyOptional.isEmpty() ||
                companyOptional.get().getCachedOn().isBefore(now.minusDays(30))) {
            try {
                BigPictureAO bigPictureAO = bigPictureService.find(domain);
                CompanyDO cacheDO = toDO(companyOptional.isEmpty() ?
                        new CompanyDO() : companyOptional.get(),
                        bigPictureAO);
                cacheDO.setCachedOn(now);
                cacheDO.setDomain(domain);
                cacheDO = companyRepository.save(cacheDO);
                cacheDO.setBreaches(hibpService.findByDomain(domain));
                return toAO(cacheDO);
            }catch (BigPictureException ex){
                throw new ApiExceptionBuilder()
                        .httpStatus(ex.getStatus())
                        .messages(new ApiReplyAOMessageBuilder()
                                .message(ex.getStatus().equals(HttpStatus.ACCEPTED) ?
                                        "Indexing. Try again later (30m?)" :
                                        ex.getStatus().getReasonPhrase())
                                .properties("domain", domain)
                                .build())
                        .build();
            }
        }else return toAO(companyOptional.get());
        //TODO calc final security score
    }

    public void update(BigPictureAO bigPictureAO) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Optional<CompanyDO> companyOptional = companyRepository.findByDomain(bigPictureAO.getDomain());
        if (companyOptional.isEmpty() ||
                companyOptional.get().getCachedOn().isBefore(now.minusDays(30))) {
            CompanyDO cacheDO = toDO(companyOptional.isEmpty() ?
                            new CompanyDO() : companyOptional.get(),
                    bigPictureAO);
            cacheDO.setCachedOn(now);
            companyRepository.save(cacheDO);
        }
    }

    private CompanyDO toDO(CompanyDO company, BigPictureAO bigPictureAO){
        company.setAddress(bigPictureAO.getLocation());
        company.setDescription(bigPictureAO.getDescription());
        company.setName(bigPictureAO.getName());
        company.setLogo(bigPictureAO.getLogo());
        company.setPhone(bigPictureAO.getPhone());
        company.setUrl(bigPictureAO.getUrl());

        company.setFacebook(bigPictureAO.getFacebook().getHandle());
        company.setLinkedin(bigPictureAO.getLinkedin().getHandle());
        company.setTwitter(bigPictureAO.getTwitter().getHandle());

        company.setIndustry(bigPictureAO.getCategory().getIndustry());
        company.setSector(bigPictureAO.getCategory().getSector());
        company.setSubIndustry(bigPictureAO.getCategory().getSubIndustry());
        company.setTags(bigPictureAO.getTags());

        //TODO FIX
        company.setSensitivityScore(BigDecimal.valueOf(0).setScale(5, RoundingMode.HALF_UP));
        return company;
    }

    private CompanyAO toAO(CompanyDO companyDO) {
        CompanyAOAbout about = new CompanyAOAbout();
        about.setAddress(companyDO.getAddress());
        about.setDomain(companyDO.getDomain());
        about.setName(companyDO.getName());
        about.setDescription(companyDO.getDescription());
        about.setLogo(companyDO.getLogo());
        about.setPhone(companyDO.getPhone());
        about.setUrl(companyDO.getUrl());

        CompanyAOType type = new CompanyAOType();
        type.setIndustry(companyDO.getIndustry());
        type.setSector(companyDO.getSector());
        type.setSubIndustry(companyDO.getSubIndustry());
        type.setTags(companyDO.getTags());

        CompanyAOSocial social = new CompanyAOSocial();
        social.setFacebook(companyDO.getFacebook());
        social.setLinkedin(companyDO.getLinkedin());
        social.setTwitter(companyDO.getTwitter());

        //TODO FIX
        CompanyAOScore score = new CompanyAOScore();
        score.setSensitivityScore(companyDO.getSensitivityScore());
        score.setBreachScore(BigDecimal.valueOf(0).setScale(5, RoundingMode.HALF_UP));
        score.setSecurityScore(BigDecimal.valueOf(0).setScale(5, RoundingMode.HALF_UP));

        CompanyAO companyAO = new CompanyAO();
        companyAO.setAbout(about);
        companyAO.setType(type);
        companyAO.setSocial(social);
        companyAO.setScore(score);
        return companyAO;
    }
}
