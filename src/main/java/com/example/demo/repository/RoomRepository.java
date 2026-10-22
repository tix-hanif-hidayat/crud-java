package com.example.demo.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.RoomModel;

public interface RoomRepository extends JpaRepository<RoomModel, Integer> {
    List<RoomModel> findAllByHotelId(Integer hotelId);
}
