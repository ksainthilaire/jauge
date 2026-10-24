package io.jauge.core.domain

import io.jauge.core.domain.usecase.AnalyzeOfferUseCase
import io.jauge.core.domain.usecase.CompleteOnboardingStepUseCase
import io.jauge.core.domain.usecase.CompleteSignInUseCase
import io.jauge.core.domain.usecase.GetNotificationsUseCase
import io.jauge.core.domain.usecase.GetPushPermissionUseCase
import io.jauge.core.domain.usecase.MarkNotificationAsReadUseCase
import io.jauge.core.domain.usecase.ObserveAuthenticationStateUseCase
import io.jauge.core.domain.usecase.ObserveIncomingCalmAlertsUseCase
import io.jauge.core.domain.usecase.ObservePreferencesUseCase
import io.jauge.core.domain.usecase.RegisterDeviceUseCase
import io.jauge.core.domain.usecase.RequestPushPermissionUseCase
import io.jauge.core.domain.usecase.SendMagicLinkUseCase
import io.jauge.core.domain.usecase.SignInWithProviderUseCase
import io.jauge.core.domain.usecase.SignOutUseCase
import io.jauge.core.domain.usecase.ComputeTrialTimelineUseCase
import io.jauge.core.domain.usecase.CreateCalmAlertUseCase
import io.jauge.core.domain.usecase.CreateShareUseCase
import io.jauge.core.domain.usecase.DeleteAccountUseCase
import io.jauge.core.domain.usecase.DeleteAnalysisUseCase
import io.jauge.core.domain.usecase.DeleteCalmAlertUseCase
import io.jauge.core.domain.usecase.DeleteShareUseCase
import io.jauge.core.domain.usecase.GetAccountUseCase
import io.jauge.core.domain.usecase.GetAnalysisHistoryUseCase
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.domain.usecase.GetCalmAlertsUseCase
import io.jauge.core.domain.usecase.GetMethodologyUseCase
import io.jauge.core.domain.usecase.GetSalaryInsightUseCase
import io.jauge.core.domain.usecase.GetSharesUseCase
import io.jauge.core.domain.usecase.GetSkillsInsightUseCase
import io.jauge.core.domain.usecase.GetSubscriptionOfferUseCase
import io.jauge.core.domain.usecase.GetSubscriptionManagementUrlUseCase
import io.jauge.core.domain.usecase.GetSubscriptionUseCase
import io.jauge.core.domain.usecase.GetTrendInsightUseCase
import io.jauge.core.domain.usecase.GetTwinOffersUseCase
import io.jauge.core.domain.usecase.PurchaseSubscriptionUseCase
import io.jauge.core.domain.usecase.RestorePurchasesUseCase
import io.jauge.core.domain.usecase.SubmitActualApplicantsUseCase
import io.jauge.core.domain.usecase.UpdateConsentsUseCase
import io.jauge.core.domain.usecase.UpdateSettingsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Koin module of the use cases. Repositories are provided by `core:data`. */
val domainModule = module {
    factoryOf(::AnalyzeOfferUseCase)
    factoryOf(::GetAnalysisHistoryUseCase)
    factoryOf(::GetAnalysisUseCase)
    factoryOf(::DeleteAnalysisUseCase)
    factoryOf(::GetSalaryInsightUseCase)
    factoryOf(::GetSkillsInsightUseCase)
    factoryOf(::GetTrendInsightUseCase)
    factoryOf(::GetMethodologyUseCase)
    factoryOf(::SubmitActualApplicantsUseCase)
    factoryOf(::GetTwinOffersUseCase)
    factoryOf(::GetCalmAlertsUseCase)
    factoryOf(::CreateCalmAlertUseCase)
    factoryOf(::DeleteCalmAlertUseCase)
    factoryOf(::CreateShareUseCase)
    factoryOf(::GetSharesUseCase)
    factoryOf(::DeleteShareUseCase)
    factoryOf(::GetAccountUseCase)
    factoryOf(::UpdateConsentsUseCase)
    factoryOf(::DeleteAccountUseCase)
    factoryOf(::GetSubscriptionUseCase)
    factoryOf(::GetSubscriptionOfferUseCase)
    factoryOf(::PurchaseSubscriptionUseCase)
    factoryOf(::RestorePurchasesUseCase)
    factoryOf(::GetSubscriptionManagementUrlUseCase)
    factory { ComputeTrialTimelineUseCase() }
    factoryOf(::ObserveAuthenticationStateUseCase)
    factoryOf(::SignInWithProviderUseCase)
    factoryOf(::SendMagicLinkUseCase)
    factoryOf(::CompleteSignInUseCase)
    factoryOf(::SignOutUseCase)
    factoryOf(::ObservePreferencesUseCase)
    factoryOf(::CompleteOnboardingStepUseCase)
    factoryOf(::UpdateSettingsUseCase)
    factoryOf(::ObserveIncomingCalmAlertsUseCase)
    factoryOf(::GetNotificationsUseCase)
    factoryOf(::MarkNotificationAsReadUseCase)
    factoryOf(::GetPushPermissionUseCase)
    factoryOf(::RequestPushPermissionUseCase)
    factoryOf(::RegisterDeviceUseCase)
}
