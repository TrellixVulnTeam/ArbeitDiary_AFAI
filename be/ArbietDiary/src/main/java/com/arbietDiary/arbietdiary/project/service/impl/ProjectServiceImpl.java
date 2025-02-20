package com.arbietDiary.arbietdiary.project.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.arbietDiary.arbietdiary.calendar.model.CalendarUserList;
import com.arbietDiary.arbietdiary.calendar.service.CalendarService;
import com.arbietDiary.arbietdiary.member.entity.Member;
import com.arbietDiary.arbietdiary.member.model.UserListInterface;
import com.arbietDiary.arbietdiary.member.repository.MemberRepository;
import com.arbietDiary.arbietdiary.member.service.WorkService;
import com.arbietDiary.arbietdiary.memberproject.entity.MemberProject;
import com.arbietDiary.arbietdiary.memberproject.model.UserResponseDto;
import com.arbietDiary.arbietdiary.memberproject.repository.MemberProjectRepository;
import com.arbietDiary.arbietdiary.project.entity.Project;
import com.arbietDiary.arbietdiary.project.model.ProjectInput;
import com.arbietDiary.arbietdiary.project.model.ProjectListInterface;
import com.arbietDiary.arbietdiary.project.model.ResponseProjectList;
import com.arbietDiary.arbietdiary.project.model.type.ProjectRoleType;
import com.arbietDiary.arbietdiary.project.repository.ProjectRepository;
import com.arbietDiary.arbietdiary.project.service.ProjectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ProjectServiceImpl implements ProjectService{
	private final MemberRepository memberRepository;
	private final ProjectRepository projectRepository;
	private final MemberProjectRepository memberProjectRepository;
	
	private final CalendarService calendarService;
	private final WorkService workService;
	
	@Override
	public CalendarUserList getUserList (Long projectId) {
		return projectRepository.findById(projectId,CalendarUserList.class);
	}
	
	@Override
	public boolean add(String userId, String projectName) {
		// TODO Auto-generated method stub
		Optional<Member> optionalMember = memberRepository.findById(userId);
		if(optionalMember.isEmpty()) {
			System.out.println("[새 프로젝트] : 회원 정보 오류");
			return false;
		}
		
		Project project = Project.builder()
				.projectName(projectName)
				.regDt(LocalDateTime.now())
				.build();
		Member member = optionalMember.get();
		
		MemberProject memberProject = MemberProject.builder()
				.member(member)
				.project(project)
				.projectRole(ProjectRoleType.MASTER)
				.regDt(LocalDateTime.now())
				.build();
		
		System.out.println("[새 프로젝트] : MemberProject = "+memberProject);
		
		projectRepository.save(project);
		workService.initWorkdays(member, project.getId());
		memberProjectRepository.save(memberProject);
		
		calendarService.makeCalendar(project);
		return false;
	}

	@Override
	public List<ResponseProjectList> getOldProject(String userId) {
		System.out.println("[기존 프로젝트] : userId = "+ userId);
		List<MemberProject> projectList = memberProjectRepository.findAllByMember_UserId(userId);
		return ResponseProjectList.memberProjectToResponseList(projectList);
	}

	@Override
	public boolean join(String userId, Long projectId) {
		// TODO Auto-generated method stub
		Optional<Member> optionalMember = memberRepository.findById(userId);
		if(!optionalMember.isPresent()) {
			System.out.println("[JOIN] : 회원정보 오류");
			return false;
		}
		
		Optional<Project> optionalProject = projectRepository.findById(projectId);
		if(!optionalProject.isPresent()) {
			System.out.println("[JOIN] : 프로젝트 오류");
			return false;
		}
		Optional<MemberProject> optionalMP = memberProjectRepository.findByProject_IdAndMember_UserId(projectId, userId);
		if(optionalMP.isPresent()) {
			System.out.println("이미 존재");
			return false;
		}
		
		Member member = optionalMember.get();
		Project project = optionalProject.get();
		MemberProject memberProject = MemberProject.builder()
				.member(member)
				.project(project)
				.projectRole(ProjectRoleType.USER)
				.regDt(LocalDateTime.now())
				.build();
		
		workService.initWorkdays(member, project.getId());
		memberProjectRepository.save(memberProject);
		return true;
	}
	
	@Override
	public boolean out(String userId, ProjectInput projectInput) {
		// TODO Auto-generated method stub
		Optional<MemberProject> optionalMemberProject = memberProjectRepository.findById(projectInput.getJoinId());	
		if(!optionalMemberProject.isPresent()) {
			System.out.println("[OUT] : 참여하시지 않았습니다.");
			return false;
		}

		MemberProject memberProject = optionalMemberProject.get();

		if(memberProject.getProjectRole().equals(ProjectRoleType.MASTER) ) {
			Long count = memberProjectRepository.countByProject_Id(memberProject.getProject().getId());
			System.out.println("[OUT] : USER & COUNT = " + count);
			if(!projectInput.getTargetId().equals(userId)) { // 다른 사람 삭제
				Optional<MemberProject> optionalTargetMember = memberProjectRepository.findByProject_IdAndMember_UserId(memberProject.getProject().getId(), projectInput.getTargetId());
				if(!optionalTargetMember.isPresent()) {
					System.out.println("Target Member is not exist");
					return false;
				}
				memberProjectRepository.delete(optionalTargetMember.get());
				return true;
			}
			if(count > 0) { // 자기 자신 삭제 && 사람 1명
				System.out.println("[아직 회원이 남아있습니다.]");
				return false;
			}
			else if(count == 0) {
				Project project = memberProject.getProject();
				memberProjectRepository.delete(memberProject);
				projectRepository.delete(project);
				return true;
			}
		}
		
		// 일반 유저 스스로 나감
		memberProjectRepository.delete(memberProject);
		return true;
	}

	@Override
	public String responseOldProject(String userId) {
		// TODO Auto-generated method stub
		UserListInterface ui = memberRepository.findByUserId(userId);

		System.out.println("???");
		
		List<ProjectListInterface> projectUserList = getProjectUserList(ui);
		
		UserResponseDto userResponse =  UserResponseDto.of(ui, projectUserList);
		ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		String json = "";
		try {
			json = ow.writeValueAsString(userResponse);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return json;
	}
	
	public List<ProjectListInterface> getProjectUserList(UserListInterface ui){
		List<ProjectListInterface> projectUserList = new ArrayList<>();
		for(UserListInterface.projects list : ui.getProjects()) {
			projectUserList.add(getProjectUser(list.getProject().getId()));  
		}
		return projectUserList;
	}
	
	public ProjectListInterface getProjectUser(Long id){
		ProjectListInterface projectListInterface = projectRepository.findById(id,ProjectListInterface.class);
		System.out.println("[Old Project] : Id = "+id);
		System.out.println(projectListInterface);
		return projectListInterface;
	}
}
