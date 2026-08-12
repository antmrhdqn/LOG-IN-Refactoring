package com.insider.login.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.insider.login.common.utils.MemberRole;
import com.insider.login.department.dto.DepartmentDTO;
import com.insider.login.department.entity.Department;
import com.insider.login.position.dto.PositionDTO;
import com.insider.login.position.entity.Position;
import com.insider.login.transferredHistory.dto.TransferredHistoryDTO;
import com.insider.login.transferredHistory.entity.TransferredHistory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MemberDTO {

    private int memberId;
    private String name;
    private String password;
    private LocalDate employedDate;
    private String address;
    private String phoneNo;
    private String memberStatus;
    private String email;
    private MemberRole role;
    private DepartmentDTO departmentDTO;
    private PositionDTO positionDTO;
    private String imageUrl;
    private String gender;
    private LocalDate birthday;

    public MemberDTO() {
    }

    public MemberDTO(int memberId) {
        this.memberId = memberId;
    }

    public MemberDTO(int memberId, String name, String password, LocalDate employedDate, String address, String phoneNo, String memberStatus, String email, MemberRole role, DepartmentDTO departmentDTO, PositionDTO positionDTO, String imageUrl, String gender, LocalDate birthday) {
        this.memberId = memberId;
        this.name = name;
        this.password = password;
        this.employedDate = employedDate;
        this.address = address;
        this.phoneNo = phoneNo;
        this.memberStatus = memberStatus;
        this.email = email;
        this.role = role;
        this.departmentDTO = departmentDTO;
        this.positionDTO = positionDTO;
        this.imageUrl = imageUrl;
        this.gender = gender;
        this.birthday = birthday;
    }

    public static List<TransferredHistoryDTO> mapTransferredHistoryList(List<TransferredHistory> transferredHistoryList) {
        List<TransferredHistoryDTO> transferredHistoryDTOList = new ArrayList<>();
        for (TransferredHistory transferredHistory : transferredHistoryList) {
            TransferredHistoryDTO transferredHistoryDTO = new TransferredHistoryDTO();
            transferredHistoryDTO.setTransferredDate(transferredHistory.getTransferredDate());
            transferredHistoryDTO.setNewPositionName(transferredHistory.getNewPositionName());
            transferredHistoryDTO.setNewDepartNo(transferredHistory.getNewDepartNo());

            transferredHistoryDTOList.add(transferredHistoryDTO);
        }
        return transferredHistoryDTOList;
    }

    public MemberDTO(MemberDTO memberDTO) {
    }

    public List<String> getRoleList() {
        if (this.role != null && !this.role.getRole().isEmpty()) {
            return Arrays.asList(this.role.getRole().split(","));
        }
        return new ArrayList<>();
    }

    public int getMemberId() {
        return memberId;
    }

    public void setMemberId(int memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDate getEmployedDate() {
        return employedDate;
    }

    public void setEmployedDate(LocalDate employedDate) {
        this.employedDate = employedDate;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public MemberRole getRole() {
        return role;
    }

    public void setRole(MemberRole role) {
        this.role = role;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public DepartmentDTO getDepartmentDTO() {
        return departmentDTO;
    }
    public void setDepartmentDTO(DepartmentDTO departmentDTO) {
        this.departmentDTO = departmentDTO;
    }


    public void setDepartment(Department department) {
        if (department != null) {
            this.departmentDTO = new DepartmentDTO();
            this.departmentDTO.setDepartNo(department.getDepartNo());
            this.departmentDTO.setDepartName(department.getDepartName());
        }
    }

    public void setPositionDTO(PositionDTO positionDTO) {
        this.positionDTO = positionDTO;
    }

    public void setPosition(Position position) {
        if (position != null) {
            this.positionDTO = new PositionDTO();
            this.positionDTO.setPositionName(position.getPositionName());
            this.positionDTO.setPositionLevel(position.getPositionLevel());
        }
    }

    public PositionDTO getPositionDTO() {
        return positionDTO;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    /* 구성원 정보를 수정할 때 비교하는 logic */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MemberDTO other = (MemberDTO) obj;
        return memberId == other.memberId &&
                Objects.equals(name, other.name) &&
                Objects.equals(password, other.password) &&
                Objects.equals(employedDate, other.employedDate) &&
                Objects.equals(address, other.address) &&
                Objects.equals(phoneNo, other.phoneNo) &&
                Objects.equals(memberStatus, other.memberStatus) &&
                Objects.equals(email, other.email) &&
                Objects.equals(role, other.role) &&
                Objects.equals(departmentDTO, other.departmentDTO) &&
                Objects.equals(positionDTO, other.positionDTO) &&
                Objects.equals(imageUrl, other.imageUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, name, password, employedDate, address, phoneNo, memberStatus, email, role, departmentDTO, positionDTO, imageUrl);
    }

    @Override
    public String toString() {
        return "MemberDTO{" +
                "memberId=" + memberId +
                ", name='" + name + '\'' +
                ", password='***'" +
                ", employedDate=" + employedDate +
                ", address='" + address + '\'' +
                ", phoneNo='" + phoneNo + '\'' +
                ", memberStatus='" + memberStatus + '\'' +
                ", email='" + email + '\'' +
                ", role=" + role +
                ", departmentDTO=" + departmentDTO +
                ", positionDTO=" + positionDTO +
                ", imageUrl='" + imageUrl + '\'' +
                ", gender='" + gender + '\'' +
                ", birthday=" + birthday +
                '}';
    }
}


