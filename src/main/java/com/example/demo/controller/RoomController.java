package com.example.demo.controller;

import java.net.URI;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.RoomModel;
import com.example.demo.repository.ExchangeRateRepository;
import com.example.demo.repository.RoomRepository;

@RestController
@RequestMapping("/api")
public class RoomController {
    private final RoomRepository roomRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public RoomController(RoomRepository roomRepository, ExchangeRateRepository exchangeRateRepository) {
        this.roomRepository = roomRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @GetMapping("/rooms")
    public List<RoomModel> listRooms() {
        return roomRepository.findAll();
    }

    @GetMapping("/hotels/{hotelId}/rooms")
    public List<RoomModel> listHotelRooms(@PathVariable int hotelId) {
        return roomRepository.findAllByHotelId(hotelId);
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<RoomModel> showRoom(@PathVariable int roomId) {
        return roomRepository.findById(roomId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomModel> addRoom(@RequestBody RoomModel room) {
        room.setId(null);
        RoomModel created = roomRepository.save(room);
        return ResponseEntity.created(URI.create("/api/rooms/" + created.getId())).body(created);
    }

    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<RoomModel> replaceRoom(
            @PathVariable int roomId,
            @RequestBody RoomModel room
        ) {
        return roomRepository.findById(roomId)
                .map(existing -> {
                    existing.setHotelId(room.getHotelId());
                    existing.setName(room.getName());
                    existing.setPrice(room.getPrice());
                    return ResponseEntity.ok(roomRepository.save(existing));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> removeRoom(@PathVariable int roomId) {
        if (!roomRepository.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }
        roomRepository.deleteById(roomId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/price")
    public ResponseEntity<?> getRoomPrice(
            @PathVariable int roomId,
            @RequestParam(required = false) String currency) {
        var room = roomRepository.findById(roomId);
        if (room.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (currency == null || currency.isBlank()) {
            return ResponseEntity.ok(room.get().getPrice());
        }

        try {
            return ResponseEntity.ok(exchangeRateRepository.convertCurrency(room.get().getPrice(), currency.toUpperCase()));
        } catch (IllegalArgumentException | IllegalStateException | DataAccessException exception) {
            return ResponseEntity.internalServerError().body("failed to convert currency");
        }
    }
}
