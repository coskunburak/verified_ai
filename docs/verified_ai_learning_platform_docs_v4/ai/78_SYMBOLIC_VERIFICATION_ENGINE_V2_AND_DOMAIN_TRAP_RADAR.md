# 78 — Symbolic Verification Engine V2, Canonical Solution Sets, Domain Trap Radar, and Counterexample Architecture

- **Specification Date:** 2026-10-31
- **Status:** CANONICAL VERIFICATION ARCHITECTURE (V6)
- **Governing Documents:** `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`, `domain/47_MATHEMATICAL_CANONICAL_REPRESENTATION.md`, `ai/27_VERIFICATION_ENGINE.md`, `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`, `adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md`

---

## 1. Symbolic Engine Selection Matrix (Sembolik Motor Karşılaştırması)

| Motor / Teknoloji | Lisans & Maliyet | Güçlü Yönleri | Neden Seçildi / Neden Elendi? | Verified AI Rolü |
|---|---|---|---|---|
| **Python + SymPy (`services/math-verifier`)** | BSD (Ücretsiz, Açık Kaynak) | Cebirsel sadeleştirme, denklem/eşitsizlik çözümü, kalkülüs (limit/türev/integral), `solveset`, `continuous_domain`, sıfır `eval` AST inşası. | **MEVCUT ÇEKİRDEK:** Hafif (`<150 MB` RAM), hızlı (`<100 ms`), 338 mevcut testle kanıtlanmış. | **V1 ve V2 Birincil Deterministik Doğrulama Motoru.** |
| **Z3 Theorem Prover (`z3-solver`)** | MIT (Ücretsiz) | Doğrusal olmayan gerçek aritmetik (`QF_NRA`), eşitsizlik sistemleri, mutlak değer ve karşı-örnek (`counterexample`) bulmada çok hızlı SMT çözücü. | `SymPy`'nin karmaşık mutlak değerli/parçalı eşitsizliklerde yavaşladığı durumlarda mükemmel tamamlayıcıdır. | **Faz 6.7 / V1.5 Yardımcı Karşı-Örnek ve Eşitsizlik Motoru.** |
| **SageMath** | GPLv3 | Çok geniş akademik cebir/sayılar teorisi. | Docker imajı `>4 GB`, soğuk başlatma süresi yüksek ve GPLv3 lisans kısıtları var. | **ELENDİ** (Lise/YKS ve Calculus 101 için gereksiz ağır). |
| **Lean 4** | Apache-2.0 | Biçimsel teorem ispatı (Formal Proof). | LLM'lerin geçerli Lean 4 taktiği üretme başarısı düşüktür ve lise cebiri için aşırı karmaşıktır. | **ELENDİ** (Akademik ispat aracı, tüketici ürünü değil). |
| **Wolfram Engine** | Ticari Lisans (Pahalı) | En geniş kapalı kaynak CAS. | Kullanıcı başına ticari API/Engine lisans maliyeti birim ekonomiyi bozar. | **ELENDİ.** |

---

## 2. What Can Be Deterministically Proven vs. Heuristically Checked (Kesin Kanıt vs. Sezgisel Kontrol Sınırları)

1. **`%100` Deterministik Olarak Kanıtlanabilenler (`VERIFIED` Statüsü Alabilir):**
   - Aritmetik ve rasyonel/köklü/üslü ifade sadeleştirmeleri (`simplify(A - B) == 0`).
   - Polinom, rasyonel ve köklü denklemlerin sonlu çözüm kümeleri (`FiniteSet`: her kökün orijinal denklemi sağladığı ve doğal tanım kümesi $D_{\text{natural}}$ içinde olduğu kanıtlanır).
   - Tek değişkenli eşitsizlikler ve aralık birleşimleri (`reduce_inequalities` + sınır/iç nokta doğrulaması).
   - Belirsiz integraller ($\frac{d}{dx}[F(x)] - f(x) = 0$ türev geri kontrolü ile `%100` kanıtlanır!).
   - Türevler, limitler ve belirli integraller.
2. **Hibrit (Sembolik + Çok Noktalı Sayısal) Kanıt Gerektirenler:**
   - Ağır trigonometrik özdeşlikler ve logaritmik dönüşümler: Sembolik `trigsimp`/`logcombine` zaman aşımına (`>800 ms`) uğrarsa, tanım kümesi içindeki 7 deterministik asal/rasyonel noktada yüksek duyarlıklı (`mpmath` 50 basamak, `abs_tol=1e-12`) sayısal eşitlik + sınır davranışı birlikte kontrol edilir.
3. **Deterministik Olarak Kanıtlanamayanlar (`VERIFIED` Yasaktır -> `UNVERIFIED` veya `NEEDS_REVIEW`):**
   - Doğal dildeki Türkçe bir hikâye sorusunun ("Ali'nin yaşının...") ilk cebirsel denkleme çevrilme adımı (Çünkü gerçek dünya semantiğinden denkleme geçiş biçimsel bir CAS işlemi değildir; yalnızca kurulan denklemin çözümü `VERIFIED` olabilir, metin->denklem çevirisi çoklu model uzlaşması ve görsel eşleme (`F08`) ile denetlenir).

> **Kritik Değişmez Kural (`VerificationPolicy.java`):** İki farklı LLM'in (`GPT-4.1-mini` ve `o4-mini`) birbirinden bağımsız olarak aynı cevabı bulması (`BLIND_CONSENSUS`), `services/math-verifier` deterministik kanıt üretmediği sürece **ASLA `VERIFIED` olarak etiketlenemez**. En fazla `UNVERIFIED` (`UNVERIFIED_BLIND_CONSENSUS_ONLY`) olabilir.

---

## 3. `CanonicalMathDocument v2` and Solution Set Algebra (`ADR-009`)

Mevcut `CanonicalSolution.java` içindeki tekil `Equality` (`x = 4`) darboğazını aşmak için `canonical_v2` şemasına aşağıdaki AST düğümleri eklenir:

```json
{
  "schemaVersion": "canonical_v2",
  "outcomeType": "SOLUTION_SET",
  "variable": "x",
  "domainRestrictions": [
    {"kind": "NotEqual", "variable": "x", "disallowedValue": {"kind": "Number", "value": "2"}}
  ],
  "extraneousRoots": [
    {"kind": "Number", "value": "2", "reasonCode": "DENOMINATOR_ZERO"}
  ],
  "solutionSet": {
    "kind": "FiniteSet",
    "elements": [
      {"kind": "Number", "value": "-2"}
    ]
  }
}
```

### Yeni Desteklenen Çözüm Kümesi Türleri (`outcomeType: SOLUTION_SET`)
- `FiniteSet`: Sonlu kök kümesi, ör. $x \in \{-2, 3\}$ (İkinci ve yüksek dereceden denklemler, mutlak değerli denklemler).
- `EmptySet`: Çözüm kümesi boş küme ($\emptyset$, ör. $x^2 + 4 = 0, x \in \mathbb{R}$).
- `Interval` & `Union`: Eşitsizlik çözüm aralıkları, ör. $(-2, 3] \cup (5, \infty)$.
- `EquationSystem`: $(x, y)$ sıralı ikili çözüm kümeleri (`TupleSet`).

---

## 4. `DomainConstraintGuard` — Tanım Kümesi ve Yalancı Kök Radarı Algoritması

YKS (`TYT`/`AYT`) ve Calculus sınavlarında öğrencilerin ve LLM'lerin en sık düştüğü tuzak, cebirsel işlemler sırasında tanım kümesini unutmaktır (ör. $\frac{x^2-4}{x-2} = 0 \implies x=2$ veya $x=-2$ bulup $x=2$'yi elememek).

`services/math-verifier/app/verifiers/domain_guard.py` içinde çalışacak deterministik algoritma:

1. **Adım 1 — Orijinal İfadenin Doğal Tanım Kümesini Çıkar ($D_{\text{natural}}$):**
   - Orijinal problem AST ağacındaki tüm bölme (`Divide(num, denom)`), çift dereceli kök (`Root(radicand, n)` için $n \equiv 0 \pmod 2$), logaritma (`Log(arg, base)`) ve $\tan/\cot$ düğümlerini tara:
     - Her `denom` için: $\text{denom}(x) \neq 0$
     - Her çift kök `radicand` için: $\text{radicand}(x) \ge 0$
     - Her logaritma `arg`, `base` için: $\text{arg}(x) > 0, \text{base}(x) > 0, \text{base}(x) \neq 1$
   - SymPy `continuous_domain(expr, x, S.Reals)` ile kesişim alarak $D_{\text{natural}} \subseteq \mathbb{R}$ kümesini oluştur.
2. **Adım 2 — Aday Kökleri Denetle (`Extraneous Root Check`):**
   - Çözücünün (veya öğrencinin) bulduğu her aday kök $r \in S_{\text{candidate}}$ için:
     - Eğer $r \notin D_{\text{natural}}$ ise:
       - $r$ kökünü `extraneous_roots` listesine neden koduyla (`DENOMINATOR_ZERO`, `EVEN_ROOT_NEGATIVE`, `LOG_NON_POSITIVE`) ekle.
       - Eğer aday çözüm $r$'yi geçerli çözüm kümesinde tutmuşsa doğrulama kararını **`CONTRADICTED` (`SOLVE_EXTRANEOUS_ROOT_INCLUDED`)** olarak işaretle!

---

## 5. `CounterexampleEngine` — Somut Karşı-Örnek Üretici Algoritması

Bir ara adım geçişi $L_i(x) \implies L_{i+1}(x)$ sembolik olarak eşdeğer olmadığında (`simplify(L_i - L_{i+1}) != 0`), öğrenciye sadece *"Bu adım yanlış"* demek yerine `services/math-verifier/app/verifiers/counterexample.py` şu deterministik aramayı çalıştırır:

1. Aday test noktaları dizisi: $P = [2, 3, -1, 1, -2, 4, \frac{1}{2}, -\frac{1}{2}, 5]$ (0 ve 1 gibi bazı hataları gizleyen triviyal noktalar sona bırakılır; ör. $(a+b)^2 = a^2+b^2$ hatasını $a=0$ gizler ama $a=2, b=3$ anında yakalar).
2. $x_0 \in P \cap D_{\text{natural}}$ için:
   - Eğer $L_i(x_0) \neq L_{i+1}(x_0)$ (veya denklem ise $L_i$ denklemini sağlayan gerçek kök $r$, $L_{i+1}$ denklemini sağlamıyorsa):
   - Döndür:
     ```json
     {
       "counterexampleFound": true,
       "point": {"x": "2"},
       "previousStepValue": "16",
       "currentStepValue": "8",
       "pedagogicalSummaryTr": "x = 2 değerini yerine koyduğumuzda önceki satır 16 verirken bu satır 8 veriyor (16 ≠ 8)."
     }
     ```
3. Bu çıktı iOS istemcisinde **Somut Kanıt Kartı (`F03`)** olarak gösterilir ve öğrencinin ikna olma süresini saniyelere indirir.
