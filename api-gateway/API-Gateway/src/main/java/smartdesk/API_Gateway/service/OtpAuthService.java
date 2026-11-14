package smartdesk.API_Gateway.service;

import smartdesk.API_Gateway.dto.RegisterRequestDTO;
import smartdesk.API_Gateway.entity.User;

public interface OtpAuthService {
    void requestOtp(String email);
    User verifyOtp(String email, String otp);
    User registerUser(RegisterRequestDTO request);
    void requestRegistrationOtp(RegisterRequestDTO request);
    User verifyRegistrationOtp(String email, String otp);
}
