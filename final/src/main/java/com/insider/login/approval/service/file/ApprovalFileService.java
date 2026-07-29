package com.insider.login.approval.service.file;

import com.insider.login.approval.dto.AttachmentDTO;
import com.insider.login.approval.entity.Attachment;
import com.insider.login.approval.repository.AttachmentRepository;
import com.insider.login.approval.service.generator.ApprovalNoGenerator;
import com.insider.login.common.error.ErrorCode;
import com.insider.login.common.error.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 전자결재 첨부파일의 디스크 I/O와 첨부 메타데이터를 단독으로 소유하는 컴포넌트.
 * 파일 경로 설정({@code file.upload-dir}/{@code file.file-dir})은 이 클래스만 안다.
 */
@Component
@Slf4j
public class ApprovalFileService {

    private static final String ORPHAN_LOG_PREFIX = "[APPROVAL_FILE_ORPHAN]";

    @Value("${file.upload-dir}")
    private String UPLOAD_DIR;

    @Value("${file.file-dir}")
    private String FILE_DIR;

    private final AttachmentRepository attachmentRepository;

    private final ApprovalNoGenerator approvalNoGenerator;

    public ApprovalFileService(AttachmentRepository attachmentRepository, ApprovalNoGenerator approvalNoGenerator) {
        this.attachmentRepository = attachmentRepository;
        this.approvalNoGenerator = approvalNoGenerator;
    }

    /** 다운로드 응답 구성에 필요한 리소스와 헤더 재료 */
    public record FileDownload(Resource resource, String contentType, String encodedFileName) {
    }

    /**
     * 첨부파일을 디스크에 저장하고 Attachment 를 DB에 저장한다.
     * 실패 시 이번 호출에서 이미 쓴 파일을 보상 삭제한 뒤 AP006 을 던진다.
     */
    public List<AttachmentDTO> store(String approvalNo, List<MultipartFile> files) {

        List<AttachmentDTO> savedList = new ArrayList<>();

        if (files == null || files.isEmpty()) {
            return savedList;
        }

        Path uploadPath = Paths.get(savePath());
        List<Path> writtenFiles = new ArrayList<>();

        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);

                String oriname = file.getOriginalFilename();
                String ext = oriname.substring(oriname.lastIndexOf("."));
                String savename = UUID.randomUUID().toString().replace("-", "") + ext;

                String fileNo = approvalNoGenerator.fileNo(approvalNo, i + 1);

                Path filePath = uploadPath.resolve(savename);
                Files.copy(file.getInputStream(), filePath);
                writtenFiles.add(filePath);
                log.info("파일 저장 됐어 : " + filePath);

                attachmentRepository.save(new Attachment(fileNo, oriname, savePath(), savename, approvalNo));

                savedList.add(new AttachmentDTO(fileNo, oriname, savePath(), savename, approvalNo));
            }

            return savedList;

        } catch (Exception e) {
            log.error("첨부파일 저장 실패 - approvalNo: {}", approvalNo, e);
            deleteQuietly(writtenFiles);
            throw new BusinessException(ErrorCode.APPROVAL_FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 결재번호에 속한 첨부파일을 디스크에서 삭제한다(DB 행은 건드리지 않는다).
     * 개별 삭제 실패는 비치명으로 두고 WARN 로그만 남긴다.
     */
    public void deleteByApprovalNo(String approvalNo) {

        List<Attachment> attachmentList = attachmentRepository.findByApprovalNo(approvalNo);

        Path uploadPath = Paths.get(savePath());

        for (Attachment attachment : attachmentList) {
            deleteQuietly(uploadPath.resolve(attachment.getFileSavename()));
        }
    }

    /**
     * 저장명으로 파일을 읽어 다운로드 재료를 구성한다.
     * 베이스 경로는 이 컴포넌트가 소유하므로 클라이언트가 보낸 경로는 사용하지 않는다.
     */
    public FileDownload loadAsResource(String savename, String oriname) {

        Path filePath = Paths.get(savePath()).resolve(savename).normalize();

        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("첨부파일 경로가 올바르지 않습니다 : " + filePath, e);
        }

        if (!resource.exists()) {
            log.warn("첨부파일이 존재하지 않습니다 : {}", filePath);
            throw new BusinessException(ErrorCode.APPROVAL_FILE_NOT_FOUND);
        }

        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("첨부파일 타입을 확인할 수 없습니다 : " + filePath, e);
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        String encodedFileName = URLEncoder.encode(oriname, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        return new FileDownload(resource, contentType, encodedFileName);
    }

    //첨부파일 저장 경로 (DB·응답에 담기는 문자열 형태를 그대로 유지한다)
    private String savePath() {
        return UPLOAD_DIR + FILE_DIR;
    }

    private void deleteQuietly(List<Path> filePaths) {
        for (Path filePath : filePaths) {
            deleteQuietly(filePath);
        }
    }

    private void deleteQuietly(Path filePath) {
        try {
            boolean deleted = Files.deleteIfExists(filePath);

            if (!deleted) {
                log.warn("{} 삭제 대상 파일이 없습니다 - savename: {}, path: {}",
                        ORPHAN_LOG_PREFIX, filePath.getFileName(), filePath);
            }
        } catch (IOException e) {
            log.warn("{} 파일 삭제 실패 - savename: {}, path: {}",
                    ORPHAN_LOG_PREFIX, filePath.getFileName(), filePath, e);
        }
    }
}
