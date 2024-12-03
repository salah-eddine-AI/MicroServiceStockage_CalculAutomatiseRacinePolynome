package com.example.demo.controller;

import java.security.SecureRandom;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.entity.Calculator;
import com.example.demo.service.EmailService;
import com.example.demo.service.UserService;

import jakarta.mail.MessagingException;

@RestController
@RequestMapping("/api/calculators")
public class CalculatorController {

    @Autowired
    private UserService userService; // Utilise UserService pour interagir avec la base
    @Autowired
    private EmailService emailService;

    @PostMapping("/login")
    public ResponseEntity<String> loginCalculator(@RequestBody Calculator loginRequest) {
        System.out.println("Tentative de connexion pour le calculator : " + loginRequest.getUsername());

        Optional<Calculator> calculator = userService.findCalculatorByUsername(loginRequest.getUsername());

        if (calculator.isPresent() && calculator.get().getPassword().equals(loginRequest.getPassword())) {
            if (!calculator.get().isVerified()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Veuillez vérifier votre e-mail.");
            }
            try {
                emailService.sendLoginNotification(calculator.get().getEmail(), calculator.get().getUsername());
            } catch (MessagingException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de l'envoi de la notification par e-mail.");
            }
            return ResponseEntity.ok("Connexion réussie.");
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Nom d'utilisateur ou mot de passe incorrect.");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerCalculator(@RequestBody Calculator calculator) {
        if (userService.findCalculatorByUsername(calculator.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Nom d'utilisateur déjà pris.");
        }

        if (userService.findCalculatorByEmail(calculator.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Email déjà utilisé.");
        }

        userService.saveCalculator(calculator);

        // Envoi d'un email de vérification
        try {
            emailService.sendVerificationEmail(calculator.getEmail(), calculator.getUsername());
        } catch (MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de l'envoi de l'email de vérification.");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body("Calculator enregistré avec succès. Veuillez vérifier votre e-mail.");
    }


    @GetMapping("/verify")
    public ResponseEntity<String> verifyEmail(@RequestParam String email) {
        Optional<Calculator> calculator = userService.findCalculatorByEmail(email);
        if (calculator.isPresent()) {
            calculator.get().setVerified(true);
            userService.saveCalculator(calculator.get());
            return ResponseEntity.ok("E-mail vérifié avec succès.");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Calculator non trouvé.");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Calculator> getCalculatorById(@PathVariable Long id) {
        Optional<Calculator> calculator = userService.findCalculatorById(id);
        return calculator.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        Calculator calculator = userService.findCalculatorByEmail(email).orElse(null);

        if (calculator == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Calculator non trouvé.");
        }

        String newPassword = generateRandomPassword();
        calculator.setPassword(newPassword);
        userService.saveCalculator(calculator);

        // Send password reset email
        try {
            emailService.sendPasswordResetEmail(email, newPassword);
        } catch (MessagingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur lors de l'envoi de l'email de réinitialisation.");
        }

        return ResponseEntity.ok("Un e-mail avec votre nouveau mot de passe a été envoyé.");
    }

    private String generateRandomPassword() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 8; i > 0; i--) {
            int index = random.nextInt(characters.length());
            password.append(characters.charAt(index));
        }
        return password.toString();
    }
}