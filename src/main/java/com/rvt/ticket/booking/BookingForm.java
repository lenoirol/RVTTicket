package com.rvt.ticket.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public class BookingForm {
    @Pattern(regexp="[a-f0-9-]{36}") private String requestKey;
    public String getRequestKey(){return requestKey;}
    public void setRequestKey(String value){requestKey=value;}
    @NotBlank @Size(max=100) private String name;
    @NotBlank @Pattern(regexp="[0-9]{10,15}", message="Số điện thoại phải có 10–15 chữ số") private String phone;
    @Email @Size(max=254) private String email;
    @NotEmpty @Size(max=8) @Valid private List<Passenger> passengers = new ArrayList<>();
    public String getName() { return name; }
    public void setName(String v) { name=v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { phone=v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email=v; }
    public List<Passenger> getPassengers() { return passengers; }
    public void setPassengers(List<Passenger> v) { passengers=v; }

    public static class Passenger {
        @PastOrPresent @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate birthDate;
        @Pattern(regexp="CCCD|HO_CHIEU|KHAI_SINH") private String documentType="CCCD";
        @NotBlank @Size(max=50) private String nationality="Việt Nam";
        public LocalDate getBirthDate(){return birthDate;} public void setBirthDate(LocalDate v){birthDate=v;}
        public String getDocumentType(){return documentType;} public void setDocumentType(String v){documentType=v;}
        public String getNationality(){return nationality;} public void setNationality(String v){nationality=v;}
        @NotBlank @Size(max=100) private String name;
        @NotBlank @Size(max=20) private String seat;
        @Size(max=30) private String document;
        @NotNull @DecimalMin("0") @DecimalMax("40") private BigDecimal luggage = BigDecimal.ZERO;
        public String getName() { return name; }
        public void setName(String v) { name=v; }
        public String getSeat() { return seat; }
        public void setSeat(String v) { seat=v; }
        public String getDocument() { return document; }
        public void setDocument(String v) { document=v; }
        public BigDecimal getLuggage() { return luggage; }
        public void setLuggage(BigDecimal v) { luggage=v; }
    }
}
