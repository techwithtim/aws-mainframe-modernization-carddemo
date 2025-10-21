package com.aws.carddemo.service;

import com.aws.carddemo.dto.CustomerDTO;
import com.aws.carddemo.exception.ResourceNotFoundException;
import com.aws.carddemo.model.Customer;
import com.aws.carddemo.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    public List<CustomerDTO> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public CustomerDTO getCustomerById(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        return convertToDTO(customer);
    }

    public CustomerDTO createCustomer(CustomerDTO customerDTO) {
        Customer customer = convertToEntity(customerDTO);
        Customer savedCustomer = customerRepository.save(customer);
        return convertToDTO(savedCustomer);
    }

    public CustomerDTO updateCustomer(Long customerId, CustomerDTO customerDTO) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        
        customer.setFirstName(customerDTO.getFirstName());
        customer.setMiddleName(customerDTO.getMiddleName());
        customer.setLastName(customerDTO.getLastName());
        customer.setAddressLine1(customerDTO.getAddressLine1());
        customer.setAddressLine2(customerDTO.getAddressLine2());
        customer.setAddressLine3(customerDTO.getAddressLine3());
        customer.setStateCode(customerDTO.getStateCode());
        customer.setCountryCode(customerDTO.getCountryCode());
        customer.setZipCode(customerDTO.getZipCode());
        customer.setPhoneNumber1(customerDTO.getPhoneNumber1());
        customer.setPhoneNumber2(customerDTO.getPhoneNumber2());
        customer.setSsn(customerDTO.getSsn());
        customer.setGovernmentIssuedId(customerDTO.getGovernmentIssuedId());
        customer.setDateOfBirth(customerDTO.getDateOfBirth());
        customer.setEftAccountId(customerDTO.getEftAccountId());
        customer.setPrimaryCardHolderIndicator(customerDTO.getPrimaryCardHolderIndicator());
        customer.setFicoScore(customerDTO.getFicoScore());
        
        Customer updatedCustomer = customerRepository.save(customer);
        return convertToDTO(updatedCustomer);
    }

    public void deleteCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        customerRepository.delete(customer);
    }

    private CustomerDTO convertToDTO(Customer customer) {
        CustomerDTO dto = new CustomerDTO();
        dto.setCustomerId(customer.getCustomerId());
        dto.setFirstName(customer.getFirstName());
        dto.setMiddleName(customer.getMiddleName());
        dto.setLastName(customer.getLastName());
        dto.setAddressLine1(customer.getAddressLine1());
        dto.setAddressLine2(customer.getAddressLine2());
        dto.setAddressLine3(customer.getAddressLine3());
        dto.setStateCode(customer.getStateCode());
        dto.setCountryCode(customer.getCountryCode());
        dto.setZipCode(customer.getZipCode());
        dto.setPhoneNumber1(customer.getPhoneNumber1());
        dto.setPhoneNumber2(customer.getPhoneNumber2());
        dto.setSsn(customer.getSsn());
        dto.setGovernmentIssuedId(customer.getGovernmentIssuedId());
        dto.setDateOfBirth(customer.getDateOfBirth());
        dto.setEftAccountId(customer.getEftAccountId());
        dto.setPrimaryCardHolderIndicator(customer.getPrimaryCardHolderIndicator());
        dto.setFicoScore(customer.getFicoScore());
        return dto;
    }

    private Customer convertToEntity(CustomerDTO dto) {
        Customer customer = new Customer();
        customer.setCustomerId(dto.getCustomerId());
        customer.setFirstName(dto.getFirstName());
        customer.setMiddleName(dto.getMiddleName());
        customer.setLastName(dto.getLastName());
        customer.setAddressLine1(dto.getAddressLine1());
        customer.setAddressLine2(dto.getAddressLine2());
        customer.setAddressLine3(dto.getAddressLine3());
        customer.setStateCode(dto.getStateCode());
        customer.setCountryCode(dto.getCountryCode());
        customer.setZipCode(dto.getZipCode());
        customer.setPhoneNumber1(dto.getPhoneNumber1());
        customer.setPhoneNumber2(dto.getPhoneNumber2());
        customer.setSsn(dto.getSsn());
        customer.setGovernmentIssuedId(dto.getGovernmentIssuedId());
        customer.setDateOfBirth(dto.getDateOfBirth());
        customer.setEftAccountId(dto.getEftAccountId());
        customer.setPrimaryCardHolderIndicator(dto.getPrimaryCardHolderIndicator());
        customer.setFicoScore(dto.getFicoScore());
        return customer;
    }
}
