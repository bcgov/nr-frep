package ca.bc.gov.nrs.frep.repository.chr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.frep.dto.frep.CheckList;
import ca.bc.gov.nrs.frep.dto.frep.Feature;
import ca.bc.gov.nrs.frep.entity.ChrChecklist;
import ca.bc.gov.nrs.frep.entity.ChrFeatureAgeXref;
import ca.bc.gov.nrs.frep.entity.ChrFeatureDetail;
import ca.bc.gov.nrs.frep.entity.ChrFeatureIdentity;
import ca.bc.gov.nrs.frep.entity.ChrFeatureTypeXref;
import ca.bc.gov.nrs.frep.entity.FrepChecklistStatusCode;
import ca.bc.gov.nrs.frep.entity.FrepResourceValue;
import ca.bc.gov.nrs.frep.entity.FrepResourceValueStatCode;
import ca.bc.gov.nrs.frep.service.ChrObjectStorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies the {@code saveFeatures} port against a mocked {@link EntityManager}: feature identity
 * and detail are persisted, the generated id is written back to the DTO, inverse-logic indicators
 * are applied, and code-keyed xrefs are created. (Real Oracle round-trip is verified in dev.)
 */
class ChrChecklistPersistenceServiceTest {

  private EntityManager entityManager;
  private ChrChecklistPersistenceService service;
  private final List<Object> persisted = new ArrayList<>();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    entityManager = mock(EntityManager.class);
    ChrObjectStorageService objectStorage = mock(ChrObjectStorageService.class);
    service = new ChrChecklistPersistenceService(objectStorage);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);

    // Managed checklist returned by find(...).
    ChrChecklist checklist = new ChrChecklist();
    checklist.setChrChecklistId(1001L);
    FrepResourceValueStatCode statCode = new FrepResourceValueStatCode();
    statCode.setFrepResourceValueStatCode("ACC");
    FrepResourceValue resourceValue = new FrepResourceValue();
    resourceValue.setFrepResourceValueStatCode(statCode);
    checklist.setFrepResourceValue(resourceValue);
    checklist.setChrChecklistAttachments(new HashSet<>());

    when(entityManager.find(ChrChecklist.class, 1001L)).thenReturn(checklist);
    lenient().when(entityManager.find(eq(FrepChecklistStatusCode.class), any()))
        .thenReturn(new FrepChecklistStatusCode());
    // All other find(...) lookups (code tables, existing detail) resolve to null.
    lenient().when(entityManager.find(eq(ChrFeatureDetail.class), any())).thenReturn(null);

    // Typed query: existing feature identities for the checklist -> none.
    TypedQuery<Object> typedQuery = mock(TypedQuery.class);
    lenient().when(entityManager.createQuery(anyString(), any(Class.class))).thenReturn(typedQuery);
    lenient().when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
    lenient().when(typedQuery.getResultList()).thenReturn(List.of());

    // Untyped query: xref/strategy delete sweeps -> none.
    Query query = mock(Query.class);
    lenient().when(entityManager.createQuery(anyString())).thenReturn(query);
    lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    lenient().when(query.getResultList()).thenReturn(List.of());

    // persist(...) captures entities and assigns the sequence id Hibernate would generate.
    doAnswer(invocation -> {
      Object entity = invocation.getArgument(0);
      if (entity instanceof ChrFeatureIdentity identity && identity.getChrFeatureId() == null) {
        identity.setChrFeatureId(5000L);
      }
      persisted.add(entity);
      return null;
    }).when(entityManager).persist(any());
  }

  @Test
  void persistsFeatureIdentityDetailAndXrefsWithInverseIndicators() {
    Feature feature = new Feature();
    feature.setFeatureLabel("1");
    feature.setCompositeFeatureInd("false");
    feature.setUnabletoLocate("true");   // inverse logic -> featureLocatedInd = "N"
    feature.setNoManagement("false");    // inverse logic -> managementAppliedInd = "Y"
    feature.setPre1846("true");          // -> ChrFeatureAgeXref code PRE1846
    feature.setBurialSite("true");       // -> ChrFeatureTypeXref code BURIALSITE

    CheckList resource = new CheckList();
    resource.setChecklistID("1001");
    resource.setStatus("ACT");
    resource.setEvaluationDate("2026-05-01");
    resource.setFeatures(new ArrayList<>(List.of(feature)));

    service.saveChecklist(resource, "IDIR\\tester");

    assertTrue(persisted.stream().anyMatch(o -> o instanceof ChrFeatureIdentity),
        "feature identity should be persisted");
    assertEquals("5000", feature.getId(), "generated id should be written back to the DTO");

    ChrFeatureDetail detail = persisted.stream()
        .filter(ChrFeatureDetail.class::isInstance)
        .map(ChrFeatureDetail.class::cast)
        .findFirst()
        .orElseThrow();
    assertEquals("N", detail.getFeatureLocatedInd(), "unabletoLocate=true -> featureLocatedInd=N");
    assertEquals("Y", detail.getManagementAppliedInd(), "noManagement=false -> managementAppliedInd=Y");

    assertTrue(persisted.stream().anyMatch(o -> o instanceof ChrFeatureAgeXref age
            && "PRE1846".equals(age.getId().getChrFeatureAgeCode())),
        "checked age PRE1846 should create an age xref");
    assertTrue(persisted.stream().anyMatch(o -> o instanceof ChrFeatureTypeXref type
            && "BURIALSITE".equals(type.getId().getChrFeatureTypeCode())),
        "checked type BURIALSITE should create a type xref");
  }
}
