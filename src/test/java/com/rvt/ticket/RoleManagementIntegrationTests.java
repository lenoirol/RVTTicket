package com.rvt.ticket;

import com.rvt.ticket.booking.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Màn hình quản lý vai trò: chỉ ADMIN truy cập, đổi vai trò buộc đăng nhập lại, luôn còn ít nhất một ADMIN. */
@SpringBootTest(properties={"ADMIN_PASSWORD=123456"}) @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RVT_SQL_TEST",matches="true")
class RoleManagementIntegrationTests {
    @Autowired JdbcTemplate db;
    @Autowired RoleService roles;
    @Autowired AccountService accounts;
    @Autowired AdminIdentityService identity;
    @Autowired MockMvc mvc;
    String unique(){return UUID.randomUUID().toString().replace("-","").substring(0,10);}
    String customer(){var name="role_"+unique();accounts.register(new RegistrationForm(name,"CustomerTest_123","Khách đổi vai trò","0900000000"));return name;}
    int customerId(String name){return db.queryForObject("SELECT TaiKhoanID FROM dbo.TaiKhoanKhach WHERE TenDangNhap=?",Integer.class,name);}
    int staffId(String name){return db.queryForObject("SELECT TaiKhoanID FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",Integer.class,name);}

    @Test void onlyAdminOpensRolePage() throws Exception {
        identity.seed();
        mvc.perform(get("/admin/vai-tro").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Vai trò &amp; phân quyền"))).andExpect(content().string(containsString("Nhân sự nội bộ")));
        mvc.perform(get("/admin/vai-tro").with(user("op").roles("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/vai-tro").with(user("c").roles("CUSTOMER","TRAVELER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/vai-tro/nhan-su").with(user("admin").roles("ADMIN")).param("username","x").param("name","x").param("role","OPERATOR").param("password","x").param("confirmation","x"))
            .andExpect(status().isForbidden());
    }
    @Test void createdOperatorLogsInWithOperatorRoleOnly() throws Exception {
        String name="op_"+unique();
        mvc.perform(post("/admin/vai-tro/nhan-su").with(csrf()).with(user("admin").roles("ADMIN")).param("username",name).param("name","Nhân viên thử").param("role","OPERATOR").param("password","OperatorTest_1").param("confirmation","OperatorTest_1"))
            .andExpect(redirectedUrl("/admin/vai-tro.html"));
        mvc.perform(post("/login").with(csrf()).param("username",name).param("password","OperatorTest_1")).andExpect(authenticated().withUsername(name).withRoles("OPERATOR"));
        assertThrows(IllegalArgumentException.class,()->roles.createStaff(name,"Trùng","OPERATOR","OperatorTest_1","OperatorTest_1","admin"));
        String taken=customer();
        assertThrows(IllegalArgumentException.class,()->roles.createStaff(taken,"Trùng khách","OPERATOR","OperatorTest_1","OperatorTest_1","admin"));
        assertThrows(IllegalArgumentException.class,()->roles.createStaff("op_"+unique(),"Ngắn","OPERATOR","short","short","admin"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyThaoTac WHERE HanhDong='TAO_NHAN_SU' AND DoiTuong=?",Integer.class,name));
    }
    @Test void staffRoleChangeRevokesSessionAndKeepsOneAdmin() throws Exception {
        identity.seed();String name="op_"+unique();roles.createStaff(name,"Nhân viên thử","OPERATOR","OperatorTest_1","OperatorTest_1","admin");
        int before=db.queryForObject("SELECT PhienBanDangNhap FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",Integer.class,name);
        roles.changeStaffRole(staffId(name),"ADMIN","admin");
        assertEquals("ADMIN",db.queryForObject("SELECT VaiTro FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",String.class,name));
        assertEquals(before+1,db.queryForObject("SELECT PhienBanDangNhap FROM dbo.TaiKhoanQuanTri WHERE TenDangNhap=?",Integer.class,name));
        assertThrows(IllegalArgumentException.class,()->roles.changeStaffRole(staffId("admin"),"OPERATOR","admin"));
        db.update("UPDATE dbo.TaiKhoanQuanTri SET VaiTro='OPERATOR' WHERE VaiTro='ADMIN' AND TenDangNhap<>?",name);
        assertThrows(IllegalArgumentException.class,()->roles.changeStaffRole(staffId(name),"OPERATOR","admin"));
        assertThrows(IllegalArgumentException.class,()->roles.changeStaffRole(staffId(name),"ROOT","admin"));
    }
    @Test void customerRoleChangeTakesEffectAfterNewLogin() throws Exception {
        String name=customer();int id=customerId(name);
        mvc.perform(post("/login").with(csrf()).param("username",name).param("password","CustomerTest_123")).andExpect(authenticated().withRoles("CUSTOMER","TRAVELER"));
        mvc.perform(post("/admin/vai-tro/khach/"+id).with(csrf()).with(user("admin").roles("ADMIN")).param("role","AGENT").param("q",name))
            .andExpect(redirectedUrl("/admin/vai-tro.html?q="+name+"&state=&page=1"));
        assertEquals("AGENT",db.queryForObject("SELECT VaiTro FROM dbo.TaiKhoanKhach WHERE TaiKhoanID=?",String.class,id));
        mvc.perform(post("/login").with(csrf()).param("username",name).param("password","CustomerTest_123")).andExpect(authenticated().withRoles("CUSTOMER","AGENT"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyThaoTac WHERE HanhDong='DOI_VAI_TRO_KHACH' AND DoiTuong=?",Integer.class,name));
        assertThrows(IllegalArgumentException.class,()->roles.changeCustomerRole(id,"ADMIN","admin"));
        mvc.perform(get("/admin/vai-tro").param("q",name).with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString(name)));
    }
}
