package demoday.backend.store.service;

import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.store.domain.StoreItem;
import demoday.backend.store.domain.MemberItem;
import demoday.backend.store.repository.MemberItemRepository;
import demoday.backend.store.repository.StoreItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storecatalog;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Transactional
class StoreCatalogIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private MemberRepository members;
    @Autowired private StoreItemRepository items;
    @Autowired private MemberItemRepository inventory;
    @Autowired private JdbcTemplate jdbc;
    private Member member;

    @BeforeEach
    void setUp() {
        member = members.saveAndFlush(Member.create(40001L, "storecat"));
    }

    @Test
    void returnsOnlyActiveItemsInIdOrderWithoutChangingBalanceOrInventory() throws Exception {
        StoreItem first = items.saveAndFlush(StoreItem.create("RECOVERY", "연속 학습 복구권", 200, true));
        items.saveAndFlush(StoreItem.create("INACTIVE", "판매 중지", 100, false));
        StoreItem last = items.saveAndFlush(StoreItem.create("ANOTHER", "다른 아이템", 300, true));

        mvc.perform(get("/api/v1/store/items").with(auth("ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].itemId").value(first.getItemId()))
                .andExpect(jsonPath("$.result[0].itemCode").value("RECOVERY"))
                .andExpect(jsonPath("$.result[0].name").value("연속 학습 복구권"))
                .andExpect(jsonPath("$.result[0].fishPrice").value(200))
                .andExpect(jsonPath("$.result[1].itemId").value(last.getItemId()));
        assertThat(member.getFishBalance()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from member_item where member_id=?", Long.class,
                member.getMemberId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from fish_transaction where member_id=?", Long.class,
                member.getMemberId())).isZero();
    }

    @Test
    void emptyCatalogReturnsEmptyList() throws Exception {
        items.saveAndFlush(StoreItem.create("HIDDEN", "판매 중지", 200, false));
        mvc.perform(get("/api/v1/store/items").with(auth("ROLE_MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    }

    @Test
    void requiresMemberRole() throws Exception {
        for (String route : List.of("/api/v1/store/items", "/api/v1/members/me/items")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).with(auth("ROLE_GUEST"))).andExpect(status().isForbidden());
        }
    }

    @Test
    void rejectsWithdrawnOrMissingMember() throws Exception {
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        members.flush();
        // SQL로 변경한 상태가 영속성 컨텍스트의 이전 값에 가려지지 않도록 비운다.
        entityManager.clear();
        var missing = new UsernamePasswordAuthenticationToken(Long.MAX_VALUE, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        for (String route : List.of("/api/v1/store/items", "/api/v1/members/me/items")) {
            mvc.perform(get(route).with(auth("ROLE_MEMBER")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_403_1"));
            mvc.perform(get(route).with(authentication(missing))).andExpect(status().isNotFound());
        }
    }

    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void documentsCatalogRoute() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/store/items'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/members/me/items'].get").exists());
    }

    @Test
    void ownedItemsAreMemberScopedIncludeInactiveAndExcludeZeroQuantity() throws Exception {
        StoreItem first = items.saveAndFlush(StoreItem.create("OWNED", "복구권", 200, true));
        StoreItem zero = items.saveAndFlush(StoreItem.create("ZERO", "소진", 100, true));
        StoreItem inactive = items.saveAndFlush(StoreItem.create("OLD", "판매 중지", 300, false));
        StoreItem otherOnly = items.saveAndFlush(StoreItem.create("OTHER", "다른 회원 전용 보유", 100, true));
        inventory.saveAndFlush(MemberItem.create(member, inactive, 3));
        inventory.saveAndFlush(MemberItem.create(member, zero, 0));
        inventory.saveAndFlush(MemberItem.create(member, first, 2));
        Member other = members.saveAndFlush(Member.create(40002L, "othercat"));
        inventory.saveAndFlush(MemberItem.create(other, otherOnly, 9));
        entityManager.clear();

        mvc.perform(get("/api/v1/members/me/items").param("memberId", other.getMemberId().toString())
                        .with(auth("ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].itemId").value(first.getItemId()))
                .andExpect(jsonPath("$.result[0].itemCode").value("OWNED"))
                .andExpect(jsonPath("$.result[0].name").value("복구권"))
                .andExpect(jsonPath("$.result[0].quantity").value(2))
                .andExpect(jsonPath("$.result[1].itemId").value(inactive.getItemId()))
                .andExpect(jsonPath("$.result[1].quantity").value(3));
        assertThat(jdbc.queryForObject("select quantity from member_item where member_id=? and item_id=?",
                Integer.class, member.getMemberId(), first.getItemId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from fish_transaction where member_id=?",
                Long.class, member.getMemberId())).isZero();
    }

    @Test
    void noOwnedItemsReturnsEmptyList() throws Exception {
        StoreItem item = items.saveAndFlush(StoreItem.create("EMPTY", "소진된 복구권", 200, true));
        mvc.perform(get("/api/v1/members/me/items").with(auth("ROLE_MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
        inventory.saveAndFlush(MemberItem.create(member, item, 0));
        mvc.perform(get("/api/v1/members/me/items").with(auth("ROLE_MEMBER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
    }

    private RequestPostProcessor auth(String role) {
        return authentication(new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
                List.of(new SimpleGrantedAuthority(role))));
    }
}
