package com.rvt.ticket.booking;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QrService {
    private final JdbcTemplate db;private final TicketService tickets;
    public QrService(JdbcTemplate db,TicketService tickets){this.db=db;this.tickets=tickets;}
    public String token(String value){
        if(value==null)throw new IllegalArgumentException("Nhập mã kiểm tra hoặc quét QR vé.");
        String raw=value.strip();if(raw.startsWith("RVT-TICKET:"))raw=raw.substring(11);
        if(!raw.matches("[A-Fa-f0-9]{64}"))throw new IllegalArgumentException("QR không phải mã vé RVT Ticket hợp lệ.");
        return raw.toUpperCase(Locale.ROOT);
    }
    public byte[] image(String value){
        try {
            var matrix=new QRCodeWriter().encode("RVT-TICKET:"+token(value),BarcodeFormat.QR_CODE,300,300,Map.of(EncodeHintType.MARGIN,4));
            var image=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<300;y++)for(int x=0;x<300;x++)image.setRGB(x,y,matrix.get(x,y)?0x000000:0xffffff);
            var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
        }catch(WriterException|IOException e){throw new IllegalStateException("Không tạo được mã QR.",e);}
    }
    public String decode(byte[] bytes){
        if(bytes.length==0||bytes.length>4*1024*1024)throw new IllegalArgumentException("Ảnh QR cần nhỏ hơn 4 MB.");
        try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw new IllegalArgumentException("Chọn ảnh PNG hoặc JPEG có mã QR.");
            var reader=readers.next();
            try {
                if(!Set.of("png","jpeg","jpg").contains(reader.getFormatName().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Chỉ hỗ trợ ảnh PNG hoặc JPEG.");
                reader.setInput(stream,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0||height<=0||(long)width*height>12000000L)throw new IllegalArgumentException("Ảnh tối đa 12 triệu điểm ảnh. Hãy cắt gần mã QR rồi thử lại.");
                var image=reader.read(0);
                var source=new RGBLuminanceSource(width,height,image.getRGB(0,0,width,height,null,0,width));
                Result result;
                try {
                    result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),Map.of(DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER,true));
                }catch(NotFoundException e){
                    // Clean exported QR images can occasionally confuse the camera-style
                    // finder-pattern detector. The pure-grid reader is a safe fallback.
                    result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),Map.of(DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE),DecodeHintType.PURE_BARCODE,true));
                }
                return token(result.getText());
            }finally{reader.dispose();}
        }catch(IOException|NotFoundException e){throw new IllegalArgumentException("Không đọc được mã QR trong ảnh. Chọn ảnh rõ nét, đủ bốn góc mã.");}
    }
    public Map<String,Object> verify(String value){
        String secret=token(value);
        var matches=db.queryForList("SELECT v.DangKyID,v.DangKyCode,v.TrangThai,o.MaDon FROM dbo.DangKyVe v JOIN dbo.DonDatVeChiTiet d ON d.DangKyID=v.DangKyID JOIN dbo.DonDatVe o ON o.DonDatVeID=d.DonDatVeID WHERE v.QrToken=?",secret);
        if(matches.isEmpty())throw new IllegalArgumentException("Mã vé không tồn tại trong hệ thống.");
        var found=matches.getFirst();var order=tickets.staffOrder((String)found.get("MaDon"));
        String state=(String)found.get("TrangThai");
        String message="DA_SU_DUNG".equals(state)?"Vé đã được sử dụng — không xác nhận lên phương tiện lần nữa.":"DA_HUY".equals(order.get("TrangThai"))?"Đơn đã hủy.":"HET_HAN".equals(order.get("TrangThai"))?"Đơn đã hết hạn.":"GIU_CHO".equals(order.get("TrangThai"))?"Đơn chưa thanh toán.":"Vé đã thanh toán. Đối chiếu hành khách và kiểm tra khung giờ trước khi xác nhận.";
        if("DA_THANH_TOAN".equals(state)&&!Boolean.TRUE.equals(order.get("CoTheCheckin")))message="Vé đã thanh toán, nhưng chưa trong khung giờ lên phương tiện hoặc chuyến đã kết thúc. Không thể xác nhận lúc này.";
        return Map.of("order",order,"ticketId",found.get("DangKyID"),"ticketCode",found.get("DangKyCode"),"message",message);
    }
}
