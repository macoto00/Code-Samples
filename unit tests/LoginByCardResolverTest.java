package cz.skodaauto.ekkv.usermanagement.features.login;

import cz.skodaauto.ekkv.api.model.LoginResult;
import cz.skodaauto.ekkv.api.model.LoginSpecificationGetter;
import cz.skodaauto.ekkv.api.model.LoginType;
import cz.skodaauto.ekkv.api.model.UserResourceGetter;
import cz.skodaauto.ekkv.common.events.CommonEvent;
import cz.skodaauto.ekkv.common.events.PerformableEvent;
import cz.skodaauto.ekkv.integrations.mfa.model.MfaUserResourceGetter;
import cz.skodaauto.ekkv.integrations.productionline.model.MultipleLoginHandling;
import cz.skodaauto.ekkv.integrations.productionline.model.ProductionLineResourceGetter;
import cz.skodaauto.ekkv.integrations.productionline.model.WorkplaceSlotResourceGetter;
import cz.skodaauto.ekkv.testing.AbstractResolverTest;
import cz.skodaauto.ekkv.usermanagement.domain.UserToken;
import cz.skodaauto.ekkv.usermanagement.domain.UserTokenRepository;
import cz.skodaauto.ekkv.usermanagement.events.producing.ProduceCreateUserByMfaUserEvent;
import cz.skodaauto.ekkv.usermanagement.events.producing.ProduceLogoutAtWorkplaceSlotEvent;
import cz.skodaauto.ekkv.usermanagement.events.producing.ProduceMfaGetCardOwnerEvent;
import cz.skodaauto.ekkv.usermanagement.events.producing.ProduceProductionLineEvent;
import cz.skodaauto.ekkv.usermanagement.events.producing.ProduceWorkplaceSlotEvent;
import cz.skodaauto.ekkv.usermanagement.exceptions.InactiveMfaCardException;
import cz.skodaauto.ekkv.usermanagement.exceptions.NoLoggedInUserAtWorkplaceException;
import cz.skodaauto.ekkv.usermanagement.exceptions.UserAlreadyLoggedInException;
import cz.skodaauto.ekkv.usermanagement.features.login.LoginByCardResolverTest.LoginByCardResolverSpy;
import cz.skodaauto.ekkv.usermanagement.features.login.model.mapping.UserTokenMapper;
import cz.skodaauto.ekkv.usermanagement.features.login.model.mapping.UserTokenMapperImpl;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginByCardResolverTest extends AbstractResolverTest<LoginByCardResolverSpy> {

    static class LoginByCardResolverSpy extends LoginByCardResolver {
        public LoginByCardResolverSpy(
                UserTokenRepository userTokenRepository,
                UserTokenMapper userTokenMapper
        ) {
            super(userTokenRepository, userTokenMapper);
        }

        @Override
        protected <E extends PerformableEvent<I, O>, I extends Serializable, O extends Serializable> Optional<O> performEvent(E event) {
            return Optional.empty();
        }

        @Override
        protected <E extends CommonEvent<?>> void publishEvent(E event) {
        }

        @Override
        protected <E extends PerformableEvent<I, O>, I extends Serializable, O extends Serializable> O performEventOrThrow(E event) {
            return null;
        }

        @Override
        protected void transactional(Runnable runnable) {
            runnable.run();
        }
    }

    private @Mock UserTokenRepository userTokenRepository;
    private final UserTokenMapper userTokenMapper = new UserTokenMapperImpl();

    @Override
    public LoginByCardResolverSpy initResolver() {
        return new LoginByCardResolverSpy(
                userTokenRepository,
                userTokenMapper
        );
    }

    // skipping other tests

    @Test
    void should_NotAllowMultipleLoginsOnDifferentWorkplaceSlots_When_WorkplaceSlotInheritProductionLineAccordingToTheProductionLineSetting() {
        // Data initialization
        // Login Specifications
        var loginSpecification1 = LoginSpecificationGetter.getLoginSpecification();
        var loginSpecification2 = LoginSpecificationGetter.getLoginSpecification(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMasterAreaId(loginSpecification1.getMasterAreaId());
            it.setStationId(loginSpecification1.getStationId());
            it.setWorkplaceId(loginSpecification1.getWorkplaceId());
        });

        // MFAUser, User and Production Line
        var mfaUserResource = MfaUserResourceGetter.getActiveMfaUserResource();
        var userResource = UserResourceGetter.getActiveUserResource();
        var productionLineResource = ProductionLineResourceGetter.getProductionLineResource(it -> {
            it.setId(loginSpecification1.getProductionLineId());
            it.setSupportsMultipleLogin(false);
        });

        // Workplace Slots with INHERIT_PRODUCTION_LINE settings
        var workplaceSlotResource1 = WorkplaceSlotResourceGetter.getWorkplaceSlotResource(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMultipleLoginHandling(MultipleLoginHandling.INHERIT_PRODUCTION_LINE);
        });
        var workplaceSlotResource2 = WorkplaceSlotResourceGetter.getWorkplaceSlotResource(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMultipleLoginHandling(MultipleLoginHandling.INHERIT_PRODUCTION_LINE);
        });

        // Mocking (Arrange)
        when(resolver.performEventOrThrow(any(ProduceMfaGetCardOwnerEvent.class)))
                .thenReturn(mfaUserResource);
        when(resolver.performEventOrThrow(any(ProduceCreateUserByMfaUserEvent.class)))
                .thenReturn(userResource);
        when(resolver.performEventOrThrow(any(ProduceProductionLineEvent.class)))
                .thenReturn(productionLineResource);
        when(resolver.performEventOrThrow(any(ProduceWorkplaceSlotEvent.class)))
                .thenReturn(workplaceSlotResource1)
                .thenReturn(workplaceSlotResource2);

        // Capturing the first UserToken as the active token
        when(userTokenRepository.save(any(UserToken.class)))
                .thenAnswer(invocation -> {
                    UserToken userToken = invocation.getArgument(0);
                    userToken.setActive(true); // Mark the token as active when saved
                    return userToken;
                });

        // Execution (Act)
        var result1 = resolver.resolve(loginSpecification1); // First login

        // Save the first UserToken as the activeUserToken
        ArgumentCaptor<UserToken> firstSaveCaptor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository, times(1)).save(firstSaveCaptor.capture());
        var activeUserToken = firstSaveCaptor.getValue();

        // Mock the repository to return the active token for the next logout operation
        when(userTokenRepository.findByActiveIsTrueAndUserId(userResource.getId()))
                .thenReturn(List.of(activeUserToken));

        // Now resolve the second login which should trigger logout for the first
        var result2 = resolver.resolve(loginSpecification2);

        // Assertion (Assert)
        ArgumentCaptor<UserToken> secondSaveCaptor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository, times(2)).save(secondSaveCaptor.capture());

        List<UserToken> capturedUserTokens = secondSaveCaptor.getAllValues();
        assertEquals(2, capturedUserTokens.size());

        // First token should be inactive after logging in to the new slot
        UserToken userToken1 = capturedUserTokens.get(0);
        assertEquals(userResource.getId(), userToken1.getUserId());
        assertEquals(loginSpecification1.getWorkplaceSlotId(), userToken1.getWorkplaceSlotId());
        assertEquals(loginSpecification1.getProductionLineId(), userToken1.getProductionLineId());

        // Second token should be active after login
        UserToken userToken2 = capturedUserTokens.get(1);
        assertEquals(userResource.getId(), userToken2.getUserId());
        assertEquals(loginSpecification2.getWorkplaceSlotId(), userToken2.getWorkplaceSlotId());
        assertEquals(loginSpecification2.getProductionLineId(), userToken2.getProductionLineId());
        assertTrue(userToken2.isActive()); // User is logged in to the new workplace slot

        assertEquals(userToken1.getProductionLineId(), userToken2.getProductionLineId());

        // Verify that the logout event is published
        verify(resolver, times(3)).publishEvent(any(ProduceLogoutAtWorkplaceSlotEvent.class));

        assertNotNull(result1);
        assertEquals(result1.getClass(), LoginResult.class);

        assertNotNull(result2);
        assertEquals(result2.getClass(), LoginResult.class);
    }

    @Test
    void should_AllowMultipleLoginsOnDifferentWorkplaceSlots_When_WorkplaceSlotAllowsMultipleLoginRegardlessOfProductionLineSetting() {
        // Data initialization
        // Login Specifications
        var loginSpecification1 = LoginSpecificationGetter.getLoginSpecification();
        var loginSpecification2 = LoginSpecificationGetter.getLoginSpecification(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMasterAreaId(loginSpecification1.getMasterAreaId());
            it.setStationId(loginSpecification1.getStationId());
            it.setWorkplaceId(loginSpecification1.getWorkplaceId());
        });

        // MFAUser, User and Production Line
        var mfaUserResource = MfaUserResourceGetter.getActiveMfaUserResource();
        var userResource = UserResourceGetter.getActiveUserResource();

        // Production Line set to NOT support multiple login
        var productionLineResource = ProductionLineResourceGetter.getProductionLineResource(it -> {
            it.setId(loginSpecification1.getProductionLineId());
            it.setSupportsMultipleLogin(false); // Production Line setting irrelevant in this case
        });

        // Workplace Slots with ALLOW_MULTIPLE_LOGIN settings
        var workplaceSlotResource1 = WorkplaceSlotResourceGetter.getWorkplaceSlotResource(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMultipleLoginHandling(MultipleLoginHandling.ALLOW_MULTIPLE_LOGIN); // Allows multiple login
        });
        var workplaceSlotResource2 = WorkplaceSlotResourceGetter.getWorkplaceSlotResource(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMultipleLoginHandling(MultipleLoginHandling.ALLOW_MULTIPLE_LOGIN); // Allows multiple login
        });

        // Mocking (Arrange)
        when(resolver.performEventOrThrow(any(ProduceMfaGetCardOwnerEvent.class)))
                .thenReturn(mfaUserResource);
        when(resolver.performEventOrThrow(any(ProduceCreateUserByMfaUserEvent.class)))
                .thenReturn(userResource);
        when(resolver.performEventOrThrow(any(ProduceProductionLineEvent.class)))
                .thenReturn(productionLineResource);
        when(resolver.performEventOrThrow(any(ProduceWorkplaceSlotEvent.class)))
                .thenReturn(workplaceSlotResource1)
                .thenReturn(workplaceSlotResource2);
        when(userTokenRepository.save(any(UserToken.class)))
                .then(AdditionalAnswers.returnsFirstArg());

        // Execution (Act)
        var result1 = resolver.resolve(loginSpecification1);
        var result2 = resolver.resolve(loginSpecification2);

        // Assertion (Assert)
        ArgumentCaptor<UserToken> argumentCaptor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository, times(2)).save(argumentCaptor.capture());

        List<UserToken> capturedUserTokens = argumentCaptor.getAllValues();
        assertEquals(2, capturedUserTokens.size());

        UserToken userToken1 = capturedUserTokens.get(0);
        assertEquals(userResource.getId(), userToken1.getUserId());
        assertEquals(loginSpecification1.getWorkplaceSlotId(), userToken1.getWorkplaceSlotId());
        assertEquals(loginSpecification1.getProductionLineId(), userToken1.getProductionLineId());
        assertTrue(userToken1.isActive());

        UserToken userToken2 = capturedUserTokens.get(1);
        assertEquals(userResource.getId(), userToken2.getUserId());
        assertEquals(loginSpecification2.getWorkplaceSlotId(), userToken2.getWorkplaceSlotId());
        assertEquals(loginSpecification2.getProductionLineId(), userToken2.getProductionLineId());
        assertTrue(userToken2.isActive());

        // Verify both tokens belong to the same production line and both are active
        assertEquals(userToken1.getProductionLineId(), userToken2.getProductionLineId());
        assertNotEquals(userToken1.getWorkplaceSlotId(), userToken2.getWorkplaceSlotId());

        assertNotNull(result1);
        assertEquals(result1.getClass(), LoginResult.class);

        assertNotNull(result2);
        assertEquals(result2.getClass(), LoginResult.class);
    }

    @Test
    void should_NotAllowMultipleLoginsOnDifferentWorkplaceSlots_When_WorkplaceSlotForbidMultipleLoginRegardlessOfProductionLineSetting() {
        // Data initialization
        // Login Specifications
        var loginSpecification1 = LoginSpecificationGetter.getLoginSpecification();
        var loginSpecification2 = LoginSpecificationGetter.getLoginSpecification(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMasterAreaId(loginSpecification1.getMasterAreaId());
            it.setStationId(loginSpecification1.getStationId());
            it.setWorkplaceId(loginSpecification1.getWorkplaceId());
        });

        // MFAUser, User and Production Line
        var mfaUserResource = MfaUserResourceGetter.getActiveMfaUserResource();
        var userResource = UserResourceGetter.getActiveUserResource();
        var productionLineResource = ProductionLineResourceGetter.getProductionLineResource(it -> {
            it.setId(loginSpecification1.getProductionLineId());
            it.setSupportsMultipleLogin(true);
        });

        // Workplace Slots with INHERIT_PRODUCTION_LINE settings
        var workplaceSlotResource1 = WorkplaceSlotResourceGetter.getWorkplaceSlotResource(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMultipleLoginHandling(MultipleLoginHandling.FORBID_MULTIPLE_LOGIN);
        });
        var workplaceSlotResource2 = WorkplaceSlotResourceGetter.getWorkplaceSlotResource(it -> {
            it.setProductionLineId(loginSpecification1.getProductionLineId());
            it.setMultipleLoginHandling(MultipleLoginHandling.FORBID_MULTIPLE_LOGIN);
        });

        // Mocking (Arrange)
        when(resolver.performEventOrThrow(any(ProduceMfaGetCardOwnerEvent.class)))
                .thenReturn(mfaUserResource);
        when(resolver.performEventOrThrow(any(ProduceCreateUserByMfaUserEvent.class)))
                .thenReturn(userResource);
        when(resolver.performEventOrThrow(any(ProduceProductionLineEvent.class)))
                .thenReturn(productionLineResource);
        when(resolver.performEventOrThrow(any(ProduceWorkplaceSlotEvent.class)))
                .thenReturn(workplaceSlotResource1)
                .thenReturn(workplaceSlotResource2);

        // Capturing the first UserToken as the active token
        when(userTokenRepository.save(any(UserToken.class)))
                .thenAnswer(invocation -> {
                    UserToken userToken = invocation.getArgument(0);
                    userToken.setActive(true); // Mark the token as active when saved
                    return userToken;
                });

        // Execution (Act)
        var result1 = resolver.resolve(loginSpecification1); // First login

        // Save the first UserToken as the activeUserToken
        ArgumentCaptor<UserToken> firstSaveCaptor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository, times(1)).save(firstSaveCaptor.capture());
        var activeUserToken = firstSaveCaptor.getValue();

        // Mock the repository to return the active token for the next logout operation
        when(userTokenRepository.findByActiveIsTrueAndUserId(userResource.getId()))
                .thenReturn(List.of(activeUserToken));

        // Now resolve the second login which should trigger logout for the first
        var result2 = resolver.resolve(loginSpecification2);

        // Assertion (Assert)
        ArgumentCaptor<UserToken> secondSaveCaptor = ArgumentCaptor.forClass(UserToken.class);
        verify(userTokenRepository, times(2)).save(secondSaveCaptor.capture());

        List<UserToken> capturedUserTokens = secondSaveCaptor.getAllValues();
        assertEquals(2, capturedUserTokens.size());

        // First token should be inactive after logging in to the new slot
        UserToken userToken1 = capturedUserTokens.get(0);
        assertEquals(userResource.getId(), userToken1.getUserId());
        assertEquals(loginSpecification1.getWorkplaceSlotId(), userToken1.getWorkplaceSlotId());
        assertEquals(loginSpecification1.getProductionLineId(), userToken1.getProductionLineId());

        // Second token should be active after login
        UserToken userToken2 = capturedUserTokens.get(1);
        assertEquals(userResource.getId(), userToken2.getUserId());
        assertEquals(loginSpecification2.getWorkplaceSlotId(), userToken2.getWorkplaceSlotId());
        assertEquals(loginSpecification2.getProductionLineId(), userToken2.getProductionLineId());
        assertTrue(userToken2.isActive()); // User is logged in to the new workplace slot

        assertEquals(userToken1.getProductionLineId(), userToken2.getProductionLineId());

        // Verify that the logout event is published
        verify(resolver, times(3)).publishEvent(any(ProduceLogoutAtWorkplaceSlotEvent.class));

        assertNotNull(result1);
        assertEquals(result1.getClass(), LoginResult.class);

        assertNotNull(result2);
        assertEquals(result2.getClass(), LoginResult.class);
    }

}
