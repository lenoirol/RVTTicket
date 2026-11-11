package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class MobileFeedbackIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    static final String PASSWORD="MobileQa_12345";
    String name(){return "qa_mobile_"+UUID.randomUUID().toString().replace("-","").substring(0,10);}
    String customer(){String name=name();accounts.register(new RegistrationForm(name,PASSWORD,"Khách kiểm thử","0900000000"));return name;}

    @Test void registrationSignsInClearsSavedAdminRequestAndRotatesSessionAndCsrf() throws Exception {
        var session=new MockHttpSession();String oldSessionId=session.getId(),username=name();
        mvc.perform(get("/admin").session(session)).andExpect(status().is3xxRedirection());
        var page=mvc.perform(get("/dang-ky").session(session)).andExpect(status().isOk()).andReturn();
        var oldToken=(CsrfToken)page.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(post("/dang-ky").session(session).param(oldToken.getParameterName(),oldToken.getToken()).param("username",username).param("password",PASSWORD).param("name","Khách kiểm thử").param("phone","0900000000"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tai-khoan/ho-so.html")).andExpect(authenticated().withUsername(username).withRoles("CUSTOMER","TRAVELER"));
        assertNotEquals(oldSessionId,session.getId());assertNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
        var profile=mvc.perform(get("/tai-khoan/ho-so").session(session)).andExpect(status().isOk()).andReturn();
        var newToken=(CsrfToken)profile.getRequest().getAttribute(CsrfToken.class.getName());assertNotEquals(oldToken.getToken(),newToken.getToken());
        mvc.perform(post("/tai-khoan/ho-so").session(session).param(newToken.getParameterName(),newToken.getToken()).param("name","Khách kiểm thử").param("phone","0900000000").param("birthDate","1985-05-10").param("documentType","CCCD").param("document","012345678901").param("nationality","Việt Nam"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tai-khoan/ho-so.html")).andExpect(flash().attributeExists("message"));
        assertEquals("012345678901",accounts.passengerDefaults(username).get("document"));
    }
    @Test void customerLoginNeverResumesAnAdminPage() throws Exception {
        String username=customer();var session=new MockHttpSession();mvc.perform(get("/admin").session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").session(session).with(csrf()).param("username",username).param("password",PASSWORD)).andExpect(redirectedUrl("/workspace.html")).andExpect(authenticated().withUsername(username));
        mvc.perform(get("/workspace").session(session)).andExpect(redirectedUrl("/tai-khoan.html"));mvc.perform(get("/tai-khoan").session(session)).andExpect(status().isOk());
    }
    @Test void passengerDefaultsAreCompleteAndDoNotExposeAuthenticationFields() {
        String username=customer();accounts.updatePassengerProfile(username,"Khách kiểm thử","0900000000","qa@example.com",LocalDate.of(1985,5,10),"CCCD","012345678901","Việt Nam");
        var defaults=accounts.passengerDefaults(username);assertEquals(5,defaults.size());assertEquals("1985-05-10",defaults.get("birthDate"));assertEquals("CCCD",defaults.get("documentType"));assertFalse(defaults.containsKey("password"));assertFalse(defaults.containsKey("MatKhauHash"));
    }
    @Test void invalidCccdDoesNotPartiallyUpdateProfile() {
        String username=customer();assertThrows(IllegalArgumentException.class,()->accounts.updatePassengerProfile(username,"Không lưu","0911111111","qa@example.com",LocalDate.of(1985,5,10),"CCCD","01234567890","Việt Nam"));
        assertEquals("Khách kiểm thử",accounts.profile(username).get("HoTen"));assertEquals("0900000000",accounts.profile(username).get("SoDienThoai"));
        accounts.updatePassengerProfile(username,"Khách kiểm thử","0900000000","",null,"CCCD","","Việt Nam");assertEquals("",accounts.passengerDefaults(username).get("document"));
    }
    @Test void homepageHasFourRealDestinationSlides() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-places"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Hạ Long"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Đà Nẵng"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Đà Lạt")));
    }
}
