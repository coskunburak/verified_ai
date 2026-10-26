# Sprint 5.4-RM — Qwen model kararı ve ADR uzlaştırması

Tarih: 2026-10-26. Karar: **OWNER_SELECTED_RESEARCH_CANDIDATE**.
Dağıtım kararı: **REQUIRES_MORE_EVIDENCE**. Üretim onayı verilmedi.

1. Sahibin seçimi `Qwen/Qwen3.5-4B` uygulama/değerlendirme adayıdır. 72–74'teki
   alternatifler tarihsel araştırma olarak korunur; bu sprintteki birincil adayın yerine geçmez.
2. [Resmî metadata](https://huggingface.co/api/models/Qwen/Qwen3.5-4B) üzerinden gözlenen
   revizyon `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`; tokenizer/processor aynı snapshot'a bağlıdır.
   Ağırlık indirilmedi. Yerel dosya checksum'ları ve deployment attestation henüz yoktur.
3. İncelenen runtime `vLLM 0.17.1`'dir. [Sürümün model tablosu](https://docs.vllm.ai/en/v0.17.1/models/supported_models/)
   Qwen3.5 mimarisini listeler. Bu belge belirli GPU, kernel veya quantization uyumluluğu kanıtı değildir.
4. İlk yol: sahibi tarafından işletilen izole Linux/NVIDIA GPU; uygulama loopback veya SSH tüneli
   üzerinden erişir. Yönetilen exact-revision API ancak ayrı gizlilik, lisans ve harcama onayıyla
   değerlendirilebilir. Mevcut adaptör harici token-faturalı API'yi kabul etmez.
5. Hedef Q4/INT4'tür; yöntem/artifact/revizyon/checksum ve karşılaştırmalı kalite kanıtı bulunmadığı
   için **BLOCKED_UNAPPROVED_ARTIFACT_AND_COMPATIBILITY**. İlk referans BF16'dır; Q4 hedefinin yerine
   geçirilmiş üretim kararı değildir. AWQ/GPTQ/GGUF ismi tek başına uygunluk sağlamaz.
6. [Resmî lisans](https://huggingface.co/Qwen/Qwen3.5-4B/blob/main/LICENSE) Apache-2.0'dır;
   koşullara tabi ticari kullanıma izin verir. LICENSE, uygulanabilir NOTICE/değişiklik bildirimleri
   korunur; marka hakkı verilmez. Sabit snapshot lisans dosyası checksum'ı ve dağıtım incelemesi beklenir.
7. Yalnızca sentetik canonical metin kullanılacak. Ham öğrenci görüntüsü, gizli düşünce izi,
   otomatik eğitim verisi toplama veya üçüncü tarafa aktarım yoktur. Endpoint servis kimlik doğrulaması,
   ağ izolasyonu ve uzak erişimde TLS/SSH gerektirir. Model yanıtı güvenilmezdir.
8. Kapı önerisi `qwen-quality-proposal-v1`: sıfır sahte VERIFIED iddiası, sıfır desteklenmeyen
   kapsamın kabulü, sıfır gizlilik olayı; schema ≥99%; bağımsız adım doğruluğu ≥95%; onaylı baseline'a
   karşı cevap doğruluğunda en fazla 2 yüzde puan non-inferiority marjı ve güven aralıkları.
   Türkçe/kritik dilim gerilemesi kabul edilmez. 8–12 s basit p95, <20 s zor rota,
   ≤$0.02 birincil ve ≤$0.05 blended maliyet yalnızca öneridir; henüz onaylı SLO/bütçe değildir.
   %95 basılı/%90 el yazısı OCR hedefleri ayrı ingestion deneyinin konusudur.
9. ADR-003 sağlayıcı bağımsız sınırı korunur. ADR-005 API-first Production V1 yaklaşımıyla izole
   çevrimdışı araştırma çelişmez. ADR-006 eğitim uygunluğu engelini korur. ADR-007 değiştirilmedi.
10. Self-hosted üretim için exact artifact, veri/lisans/gizlilik, yeterli bağımsız kalite,
    kapasite/latency, tam TCO, operasyon, shadow/canary ve rollback kanıtları; açık sahibi onayı gerekir.
11. Bu sprint için mevcut ADR değişikliği gerekmez. Üretim varsayılanı self-hosted-first olacaksa
    ADR-005 ile açık uzlaştırma/amendment ve ADR-007 kapsamında model replacement kararı gerekir.
    Konfigürasyon bayrağı bu kararı ikame edemez; mevcut kod staging/production SOLVE'u engeller.

Makine okunabilir sözleşme: `infra/local/qwen-model-release.json`.
`null` alanlar eksik kanıttır, sıfır maliyet veya başarılı kontrol anlamına gelmez.
Model alias'ındaki revision kimlik kontrolüne yardımcı olur; `/v1/models` ağırlıkların kriptografik
kanıtı değildir. Sahibi exact snapshot checksum'larını, image digest'ini, başlatma ayarlarını ve GPU
kimliğini ayrı deployment evidence dosyasında bağlar; dosya SHA-256'sı runtime konfigürasyonunda zorunludur.

Gemma 4 12B, Ministral 3 14B Reasoning ve Nemotron-Cascade-2-30B-A3B yalnızca sahibin gelecekteki
escalation rol adayları olarak kaydedilmiştir. Bu sprintte doğrulanmadı, deploy edilmedi, çağrılmadı.
Provider fallback erişilebilirlik kurtarmasıdır; bağımsız ikinci çözücü kalite işlemidir. Gelecekte
ikincil çözücüye authoritative source verilir, birincil modelin gizli akıl yürütmesi aktarılmaz.
