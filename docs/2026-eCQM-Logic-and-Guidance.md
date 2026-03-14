![][image1]**Centers for Medicare & Medicaid Services** 

**Electronic Clinical Quality Measure (eCQM) Logic and Implementation Guidance** 

**Version 9.0**  

**May 2025**  
Centers for Medicare & Medicaid Services Table of Contents 

**Table of Contents** 

**1\. Introduction.............................................................................................................................1** 1.1 eCQM Types...................................................................................................................2 1.2 Population Basis..............................................................................................................2 

1.2.1 Patient-based eCQMs..........................................................................................2 1.2.2 Episode-based eCQMs........................................................................................2 1.3 eCQM Scoring ................................................................................................................3 1.3.1 Proportion eCQMs..............................................................................................3 1.3.2 Continuous Variable Measures...........................................................................6 1.3.3 Ratio Measures....................................................................................................7 1.4 Hybrid Measures .............................................................................................................8 1.5 Program Candidate eCQMs............................................................................................9 **2\. Clinical Quality Language Measure Logic .........................................................................10** 2.1 Using CQL Logic to Evaluate QDM Elements.............................................................10 2.2 Understanding CQL Basics ...........................................................................................11 2.3 Libraries........................................................................................................................12 2.4 Queries..........................................................................................................................13 2.4.1 Where Clause ....................................................................................................13 2.4.2 Relationships – With and Without Clauses.......................................................14 2.5 Timing Calculations ......................................................................................................14 2.5.1 Duration ............................................................................................................15 2.5.2 Difference..........................................................................................................15 2.5.3 Intervals.............................................................................................................16 **3\. Data Elements and Value Sets.............................................................................................17** 3.1 eCQM Data Element Repository ..................................................................................17 3.2 Value Set Location and Tools.......................................................................................17 3.3 Direct Reference Codes.................................................................................................18 3.4 QDM Category and Code System .................................................................................19 3.5 Drug Representations Used in Value Sets ....................................................................20 3.6 Discharge Medications..................................................................................................20 3.7 Allergies to Medications and Other Substances ...........................................................21

Electronic Clinical Quality Measure Logic and Implementation Guidance ii Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Table of Contents 

3.8 Principal Diagnosis in Inpatient Encounters.................................................................22 3.9 Medical Reason, Patient Reason, System Reason ........................................................22 3.10 Activities That Were “Not Done” .................................................................................23 3.11 Entities ..........................................................................................................................25 3.12 Supplemental Value Sets...............................................................................................25 

3.12.1 Race and Ethnicity ............................................................................................26 3.12.2 Sex.....................................................................................................................27 3.13 ICD-9 and ICD-10 Codes in Value Sets .......................................................................27 3.13.1 Use of Nonclinical or Administrative Code Systems .......................................27 3.14 Display of Human-Readable HQMF ............................................................................28 **4\. eCQM Guidance ...................................................................................................................29 5\. ASTP/ONC Project Tracking System (Jira)......................................................................30 6\. CMS Quality Program Helpdesks.......................................................................................32 Version History ............................................................................................................................33 Appendix A. Standards and Code Systems...............................................................................36 Appendix B. Time Interval Definitions and Examples.............................................................37** Interval Operators...................................................................................................................37 Timing Phrases .......................................................................................................................37 **Acronyms ......................................................................................................................................43**

Electronic Clinical Quality Measure Logic and Implementation Guidance iii Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Table of Contents 

**Tables** 

6.1. Helpdesk Contact Information for CMS Quality Reporting Programs .............................32 B.1. Time Interval Definitions and Examples...........................................................................40 

**Figures** 

1.1. Initial Population Example for a Patient-based eCQM........................................................2 1.2. Initial Population Example for an Episode-based eCQM....................................................3 1.3. Reporting Stratification........................................................................................................5 1.4. Stratification.........................................................................................................................5 1.5. Performance Rate Calculation Defined in Header for Multiple Populations ......................5 1.6. Multiple Numerators............................................................................................................6 2.1. Sample Definition Statement.............................................................................................10 2.2. Example with Boolean Return ...........................................................................................12 2.3. Example with List Return ..................................................................................................12 2.4. Use of Libraries in Definitions ..........................................................................................12 2.5. Where Clause .....................................................................................................................13 2.6. With Clause Example ........................................................................................................14 2.7. Without Clause Example ...................................................................................................14 3.1. Direct Reference Codes .....................................................................................................19 3.2. Terminology Aligned with Direct Reference Codes from 3.1...........................................19 3.3. Definition Using Multiple Data Elements to Address Terminology Requirements ..........20 3.4. Discharge Medication Example.........................................................................................21 3.5. Principal Diagnosis Example.............................................................................................22 3.6. Denominator Exception Example ......................................................................................23 3.7. Example of “Not Done” in CQL ........................................................................................24 3.8. Corresponding QRDA I: Example of a Negation Instance “Not Done” ...........................24 B.1. Interval Comparison Operators..........................................................................................37 B.2. Interval Starts before Start .................................................................................................38 B.3. Interval Starts before Start with Offset ..............................................................................38 B.4. Interval Starts Within.........................................................................................................39

Electronic Clinical Quality Measure Logic and Implementation Guidance iv Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

**1\. Introduction** 

The Centers for Medicare & Medicaid Services (CMS) provides this guidance document for use  with the updated hospital \- inpatient, hospital \- outpatient, and eligible clinician1 electronic  clinical quality measure (eCQM) specifications. CMS released the eCQM specifications in May  2025 for users and implementers of the eCQMs for calendar year 2026 reporting/performance  under CMS’s quality reporting and value-based purchasing programs. Please note, however, that  the eCQM examples provided throughout this guidance document draw from the eCQM  specifications posted for 2025 reporting/performance period.  

eCQMs will not be eligible for 2026 reporting until CMS proposes and finalizes them through  notice, public comment, and rulemaking for each applicable program. This document  conceptualizes eCQM logic and data elements for Quality Data Model (QDM) measures and is  intended for implementers. CMS strongly recommends review of this document to understand  the intent and operation of each eCQM before implementation.  

This document is organized as follows:  

• Sections 2 through 4 provide general implementation guidance, including how to  conceptualize and address specific logic and data elements during eCQM  implementation.  

• Section 5 provides information to interested parties on how to use the Assistant Secretary  for Technology Policy/Office of the National Coordinator for Health Information  Technology (ASTP/ONC) Project Tracking System (Jira) to provide feedback; track  issues; ask questions about eCQM intent, specifications, certification, and standards; and  address issues uncovered during implementation of the eCQMs. 

• Section 6 provides contact information for the various eCQM-related CMS help desks. 

• The appendices provide information on where to find the standards and code systems  used in conjunction with the updated eCQMs as well as examples of timing intervals used  in eCQM logic.  

For additional information and guidance on implementing eCQM updates for 2026  reporting/performance, please refer to the eCQM Implementation Checklist and the tools,  resources, and standards used by eCQMs provided on the Electronic Clinical Quality  Improvement (eCQI) Resource Center.  

1 Please note that this guide formerly referred to “eligible clinician eCQMs” as “EC eCQMs.” 

Electronic Clinical Quality Measure Logic and Implementation Guidance 1  Version 9.0 May 2025    
Centers for Medicare & Medicaid Services 1\. Introduction 

**1.1 eCQM Types** 

CMS classifies eCQMs based on the unit of analysis—patients or episodes—and the method  used to compute the score, whether by proportion, continuous variable, ratio, or count. This  section describes these classifications and provides details on computing eCQMs.  

**1.2 Population Basis** 

The Guidance section in the header of each eCQM includes a statement to indicate whether the  eCQM is patient based or episode based. This section describes both the patient-based and  episode-based eCQMs. 

**1.2.1 Patient-based eCQMs** 

Patient-based eCQMs evaluate the care of a patient and assign the patient to membership in one  or more eCQM segments or populations. Most eligible clinician eCQMs are patient based. 

All information in the patient record referenced in the eCQM should be considered when  computing a patient-based measure. The criteria for inclusion of a patient in an eCQM  population might require satisfying conditions across multiple patient encounters or episodes of  care. For example, a patient can receive a diagnosis and initial treatment during one office visit  and then have ongoing treatment associated with that same diagnosis during several follow-up  visits or episodes of care.  

To identify which patients the measured entity should count in a patient-based eCQM, review the  Guidance section of the header and the context of the eCQM logic section. For example, in  CMS124v13, Cervical Cancer Screening*,* patients included in the eCQM are females 24–64  years of age by the end of the measurement period with a visit during the measurement period, as  defined in the eCQM’s initial population (Figure 1.1).  

**Figure 1.1. Initial Population Example for a Patient-based eCQM** 

| ◢ Initial Population  AgeInYearsAt(date from   end of "Measurement Period"  )in Interval\[24, 64\]  and exists ( \["Patient Characteristic Sex": "Female"\] )  and exists "Qualifying Encounters" |
| :---- |

**1.2.2 Episode-based eCQMs** 

Episode-based eCQMs evaluate the care during an encounter with a measured entity and assign  the episode of care to one or more eCQM population segments. All hospital \- inpatient and  hospital \- outpatient eCQMs are episode-based eCQMs, and a few eligible clinician eCQMs are  episode-based eCQMs.  

In an episode-based eCQM, the initial population identifies the episodes of care. An episode is  based on a specific event referenced in other segments of the eCQM, such as the denominator or  the numerator.  

To identify the encounters or procedures counted in an episode-based eCQM, review the  Guidance section of the header and the context of the eCQM logic section. For example, for 

Electronic Clinical Quality Measure Logic and Implementation Guidance 2  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

CMS133v13, Cataracts: 20/40 or Better Visual Acuity within 90 Days Following Cataract  Surgery, the unit of analysis is the cataract surgery procedure, as defined in the initial population  (Figure 1.2).  

**Figure 1.2. Initial Population Example for an Episode-based eCQM** 

| ◢ Initial Population  “Cataract Surgery Between January and September of Measurement Period” CataractSurgeryPerformed where AgeInYearsAt(date from start of "Measurement Period")\>=18  |
| :---- |

In this example, the initial population includes all cataract surgery procedures performed  between January and September of the measurement period in which the patient was 18 years of  age or older at the start of the measurement period.  

Please note that swing bed encounters should not be included in episode-based hospital \-  inpatient eCQMs. Therefore, implementers must work with their electronic health record (EHR)  vendors to remove swing bed encounters from measures. 

 **1.3 eCQM Scoring**  

**1.3.1 Proportion eCQMs** 

Most of the eCQMs in current CMS reporting programs are proportion eCQMs. A proportion  eCQM assigns the scored entities, either patients or episodes, to the populations and strata  defined by an eCQM, and computes the appropriate rates.  

The populations defined by a proportion measure include the following:2  

• **Initial population (IP):** All events for measured entities to evaluate regarding a quality measure involving patients or episodes that share a common set of characteristics within a specific measurement set to which a given eCQM belongs. Subsequent eCQM populations (for example, numerator, denominator) draw patients or episodes from the initial population. 

• **Denominator (DENOM):** The lower part of a fraction used to calculate a proportion or rate. It can be the same as the initial population or a subset of the initial population to further constrain the population for the purpose of the measure. 

• **Denominator exclusions (DENEX):** A patient or episode that measured entities remove from the denominator before determining if numerator criteria are met. For example, a measure evaluating the existence of foot examinations for patients would list patients with bilateral lower extremity amputations as a denominator exclusion. 

• **Numerator (NUMER):** The upper portion of a fraction used to calculate a proportion or rate. Also called the measure focus, it is the target process, condition, event, or outcome. Numerator criteria are the processes or outcomes of interest for each patient, procedure, or other unit of measurement defined in the denominator for proportion measures. A 

2 Most definitions are also available at the eCQI Resource Center Glossary. Note, these definitions are not eCQM specific. For more information on how to calculate quality measures, please refer to the MMS Hub Measure  Calculations guide, saved as a supplemental material on the MMS Resources and Templates page.

Electronic Clinical Quality Measure Logic and Implementation Guidance 3  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

numerator statement describes the clinical action satisfying the conditions of the  performance measure. 

• **Denominator exceptions (DEXCEP):** Any condition that removes a unit of measurement (patients or episodes) from the denominator of the performance rate only if the patient or episode does not meet the numerator criteria. A denominator exception allows for adjustment of the calculated score for those measured entities with higher risk populations or for exercise of clinical judgment while performing care. Allowable reasons for a denominator exception fall into three general categories: (1) medical reasons, (2) patient reasons, or (3) system reasons. Only proportion measures use denominator exceptions. When removing denominator exception cases from the denominator, the measured entity may be required to report the number of patients or episodes with valid exceptions. 

• **Numerator exclusions (NUMEX):** Defines an instance that measured entities should not include in the numerator data. Numerator exclusions are used only in ratio and proportion measures. 

To compute a proportion measure:  

• Identify the patients or episodes of care in the IP using the initial population criteria. • Refine the IP by applying denominator criteria to identify the DENOM cases. 

• Review DENOM cases against denominator exclusion criteria. If a case meets denominator exclusion criteria, label it as DENEX and remove it from consideration for the NUMER. 

• Assess all remaining cases—all DENOM cases that do not meet denominator exclusion criteria—against the numerator criteria. Label all cases meeting the numerator requirements as NUMER. 

• Identify cases that do not meet numerator requirements and assess them against denominator exception requirements. If a case meets denominator exception requirements, then label the case as DEXCEP. 

• Identify cases meeting numerator requirements and evaluate them against the numerator exclusion requirements. If a case meets the numerator exclusions, then label the case as NUMEX. 

**1.3.1.1 Reporting Stratification** 

Proportion eCQMs might also have reporting strata defined for an eCQM. Strata are variables  defining a subdivision of the eCQM for reporting, such as reporting separately by age group (for  example, 14-19, 20-25). For eCQMs, the human-readable document includes a Stratification  section. If an eCQM does not have reporting strata defined, it displays “None” as the default. If  an eCQM contains reporting stratification data, the measure developer lists each stratum  separately. For example, CMS159v13, Depression Remission at Twelve Months*,* contains two  strata as stated in the header and noted in Figure 1.3. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 4  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

**Figure 1.3. Reporting Stratification**  

| Stratification  | Ages 12 to 17 at the time of the index assessment  Ages 18 and older at the time of the index assessment |
| :---: | :---- |

In addition, the strata are defined under the Population Criteria and Definition sections of the  logic. For brevity, only one is shown below in Figure 1.4.  

**Figure 1.4. Stratification** 

| exists ( \["Patient Characteristic Birthdate": "Birth date"\] BirthDate  with "Index Depression Assessment" IndexAssessment   such that AgeInYearsAt(date from start of   Global."NormalizeInterval"(IndexAssessment.relevantDatetime, IndexAssessment.relevantPeriod)) in  Interval\[12, 17\]  ) |
| :---- |

**1.3.1.2 Performance Rate Aggregation**  

Specific programs may require reporting of performance rates. The performance rate is the  number of patients or episodes in the NUMER, accounting for NUMEX, divided by the number  of patients or episodes in the DENOM, accounting for DENEX and DEXCEP. Calculate  performance rate using the formula: 

Performance Rate \= (NUMER – NUMEX) / (DENOM – DENEX – DEXCEP) 

Some eCQMs have more than one population that are components of the overall calculation of a  single performance rate. In this instance, the header specifies the performance rate calculation.  Figure 1.5 provides an example of a defined rate aggregation for CMS145v13, Coronary Artery  Disease (CAD): Beta-Blocker Therapy-Prior Myocardial Infarction (MI) or Left Ventricular  Systolic Dysfunction (LVEF less than or equal to 40%). 

**Figure 1.5. Performance Rate Calculation Defined in Header for Multiple Populations** 

| Rate   Aggregation | This measure is intended to have one reporting rate, which aggregates the following  populations into a single performance rate for reporting purposes:   • Population 1: Patients with left ventricular systolic dysfunction (LVEF \<=40%) • Population 2: Patients with a prior (within the past 3 years) myocardial infarction  For the purposes of this measure, a single performance rate can be calculated as follows:  Performance Rate \= (Numerator 1 \+ Numerator 2)/ \[(Denominator 1 – Denominator  Exceptions 1\) \+ (Denominator 2 – Denominator Exceptions 2)\] |
| :---- | :---- |

**1.3.1.3 Multiple Numerators** 

For eCQMs with multiple numerators, the measured entity must score each patient or episode for  inclusion or exclusion in each population. If an eCQM has more than one numerator and the first  numerator includes the patient, the measured entity should evaluate the same patient for  inclusion in additional numerators as well. Figure 1.6 provides an example of an eCQM,  CMS128v13, Antidepressant Medication Management, with multiple numerators. If any patients  in this eCQM meet the conditions for Numerator 1, the measured entity should also evaluate  these patients to determine if they also meet the conditions for Numerator 2\.

Electronic Clinical Quality Measure Logic and Implementation Guidance 5  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

**Figure 1.6. Multiple Numerators**  

| Numerator  | Numerator 1: Patients who have received antidepressant medication for at least 84 days (12  weeks) of continuous treatment beginning on the IPSD through 114 days after the IPSD (115  total days).  Numerator 2: Patients who have received antidepressant medications for at least 180 days (6  months) of continuous treatment beginning on the IPSD through 231 days after the IPSD (232  total days). |
| :---: | :---- |

When the eCQM definition includes stratification, the measured entity should report each  population in the eCQM definition both without stratification and stratified by each stratification  criterion.  

**1.3.2 Continuous Variable Measures** 

Continuous variable eCQMs can be patient-based or episode-of-care eCQMs. A continuous  variable is a measure score in which each individual value for the measure can fall anywhere  along a continuous scale. These measures include the following elements:3 

• **Initial population (IP):** All events for measured entities to evaluate regarding a quality measure involving patients or episodes that share a common set of characteristics within a specific measurement set to which a given eCQM belongs. Subsequent eCQM populations (for example, measure population) draw patients or episodes from the initial population. 

• **Measure population (MSRPOPL):** The measure population defines the criteria that patients or episodes must meet to be included in the measure calculation. It can be identical to, or a subset of, the initial population—for example, all patients seen in the emergency department during the measurement period. 

• **Measure population exclusions (MSRPOPLEX):** A subset of the measure population that the measure observation calculations do not use. 

• **Measure observations:** The computation that measured entities should perform on the members of the measure population after removing the measure population exclusions. For example, CMS986v4, Global Malnutrition Composite Score, assesses the percentage of hospitalizations of adults aged 65 years and older at the start of the inpatient encounter during the measurement period, with a length of stay equal to or greater than 24 hours, who received optimal malnutrition care where care performed was appropriate to the patient's level of malnutrition risk and severity. 

To compute a continuous variable eCQM, take the following steps:  

• Identify the patients or episodes of care in the IP using the initial population criteria. • Refine the IP by applying measure population criteria to identify the MSRPOPL cases. 

3 Most definitions are also available at the eCQI Resource Center Glossary. Note, these definitions are not eCQM specific. For more information on how to calculate quality measures, please refer to the MMS Hub Measure  Calculations guide, saved as a supplemental material on the MMS Resources and Templates page. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 6  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

• Review MSRPOPL cases against measure population exclusion criteria. If a case meets measure population exclusion criteria, label it as a MSRPOPLEX and remove it from consideration for the measure observations. 

• Evaluate each remaining member of the MSRPOPL against the defined measure observation criteria and aggregate the results using the specified operator. 

As with proportion eCQMs, continuous variable eCQMs might have stratification requirements.  Report performance results for each population without stratification and for each defined  stratum separately. The initial population and measure population require specifying the number  of patients or episodes falling into each of these populations without stratification as well as  those populations stratified by any defined strata.  

**1.3.3 Ratio Measures** 

Ratio measures can be either patient-based or episode-of-care measures. They include the  following elements: 

• **Initial population (IP):** All events for measured entities to evaluate regarding a quality measure involving patients or episodes that share a common set of characteristics within a specific measurement set to which a given eCQM belongs. Subsequent eCQM populations (for example, numerator, denominator) draw patients or episodes from the initial population(s). Note that there may be different initial populations for the denominator and numerator of ratio measures. 

• **Denominator (DENOM):** The lower part of a fraction used to calculate a ratio. It can be the same as the initial population or a subset of the initial population to further constrain the population for the purpose of the measure. 

• **Denominator exclusions (DENEX):** A patient or episode that measured entities remove from the denominator. In ratio measures, because the denominator and numerator flow separately from the initial population, patients who meet the denominator exclusions criteria are only removed from the denominator, not the numerator. 

• **Numerator (NUMER):** The upper portion of a fraction used to calculate a ratio. Also called the measure focus, it is the target process, condition, event, or outcome. Numerator criteria are the processes or outcomes of interest for each patient, procedure, or other unit of measurement defined in the initial population for ratio measures. A numerator statement describes the clinical data element satisfying the conditions of the performance measure. The numerator is not a subset of the denominator for ratio measures. 

• **Numerator exclusions (NUMEX):** Defines an instance that measured entities should not include in the numerator data. Numerator exclusions are used only in ratio and proportion measures. 

• **Measure observations:** The computation that measured entities should perform on the cases of the numerator and denominator after removing the exclusions. For example, Measure Observation 1, associated with the denominator of the ratio eCQM CMS871v4, Hospital Harm \- Severe Hyperglycemia*,* computes the number of inpatient hospital days that match the initial population/denominator criteria and do not meet denominator exclusion criteria. Measure observations may be associated with the denominator or numerator.

Electronic Clinical Quality Measure Logic and Implementation Guidance 7  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

The following steps are used to compute a ratio measure denominator:  

1\. Identify the patients or episodes of care in the IP using the initial population criteria. 2\. Refine the IP by applying denominator criteria to identify the DENOM cases. The DENOM may equal the IP. 

3\. Review DENOM cases against denominator exclusion criteria. If a case meets denominator exclusion criteria, label it as a DENEX and remove it from the DENOM. 4\. Evaluate each remaining member of the DENOM (for example, all cases that meet the DENOM and *not* DENEX*)* against the defined measure observations criteria and aggregate the results using the specified operator. 

The following steps are used to compute a ratio measure numerator: 

1\. Identify the patients or episodes of care in the IP using the initial population criteria. 2\. Refine the IP by applying numerator criteria to identify the NUMER cases. 3\. Review NUMER cases against numerator exclusion criteria. If a case meets numerator exclusion criteria, label it as a NUMEX and remove it from the NUMER. 

4\. Evaluate each remaining member of the NUMER (for example, all cases that meet the NUMER and *not* NUMEX*)* against the defined measure observations criteria and aggregate the results using the specified operator. 

Aggregate scores for ratio measures can be more than just the counts of cases in each population.  In addition to the identification of measure population(s), ratio measures can define observations  on cases falling into various populations and then aggregate these individual observations  according to aggregation rules specific to each measure. 

In ratio measures, for each population, the measure developer should use individual observations  (for example, measurements or calculations) for denominator and numerator cases, and then use  them to calculate the aggregate ratio: 

Ratio: Aggregate NUMER / Aggregate DENOM 

Calculate the aggregate DENOM using individual observations for all cases in the DENOM and  not in the DENEX and calculate the aggregate NUMER using individual observations for all  cases in the NUMER and not in the NUMEX. For more detailed examples of ratio measure  calculations, please reference the MMS Hub Measure Calculations guide, saved as a  supplemental material on the MMS Resources and Templates page.  

Unlike proportion and continuous variable measures, ratio measures cannot apply stratification  requirements unless the numerator and denominator are pulled from the same initial population.  

**1.4 Hybrid Measures** 

Hybrid measures are quality measures merging data elements from two or more sources to  calculate measure results (for example, EHR and claims data). These measures require updates to  both the electronic specifications and claims-based specifications, available on QualityNet. For  more information on hybrid measures, please see the hybrid measures subsection of the eCQI  Resource Center.

Electronic Clinical Quality Measure Logic and Implementation Guidance 8  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 1\. Introduction 

**1.5 Program Candidate eCQMs**  

Program candidate eCQMs are not eligible for CMS quality reporting until CMS proposes and  finalizes them through notice-and-comment rulemaking for each applicable program. Program  candidate measures can be found on the eCQI Resource Center website in designated Hospital \-  Inpatient, Hospital \- Outpatient, and Eligible Clinician subsections by filtering on the  reporting/performance period and “Program Candidate eCQMs.”

Electronic Clinical Quality Measure Logic and Implementation Guidance 9  Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

**2\. Clinical Quality Language Measure Logic** 

eCQMs use Clinical Quality Language (CQL) logic and the QDM to harmonize standards  between clinical decision support and eCQM reporting. CQL is a clinically focused, high-level  query language that can express sophisticated eCQM logic. A significant feature of CQL is its  use of libraries, which are collections of CQL definitions or function statements that eCQM and  clinical decision support artifact developers can share across and between eCQMs and decision  support rules. The use of shared functions and definitions results in greater consistency across  eCQMs and enables developers to reuse the same statements.  

Several Health Level Seven International (HL7®) implementation guides (IGs) and related  resources provide direction on using CQL expressions and QDM data elements in the eCQM via  the Health Quality Measures Format (HQMF). The following resources are primary sources for  interpreting eCQM representation:  

• HL7 Version 3 Implementation Guide: Clinical Quality Language (CQL)-based Health Quality Measure Format (HQMF), Release 1, Standard for Trial Use 4.1—US Realm 

• Clinical Quality Language Specification, Release 1 Mixed Normative/Trial-Use (CQL 1.5) 

• CQL Formatting and Usage Wiki 

• QDM v5.6 

Subsections 2.1 through 2.6 provide an overview of this material, including common logic  expressions and proper usage.  

Visit the CQL page on the eCQI Resource Center for additional information and education.  **2.1 Using CQL Logic to Evaluate QDM Elements**   
A QDM eCQM consists of populations, such as denominator or numerator, that are composed of  a combination of QDM data elements and CQL logic to form expressions. These expressions  define the criteria for membership in each population based on the intent of the eCQM. 

As shown in Figure 2.1, this definition statement establishes the global criteria for an inpatient  encounter.  

**Figure 2.1. Sample Definition Statement** 

| ◢ Global.Inpatient Encounter  \[“Encounter, Performed”: “Encounter Inpatient”\] EncounterInpatient  where EncounterInpatient.relevantPeriod ends during day of “Measurement Period” |
| :---- |

The first part of this definition statement, enclosed in brackets, references a QDM datatype  (“Encounter, Performed”) and a value set indicating the specific codes meeting encounter criteria  for this eCQM (“Encounter Inpatient”). The combination of a QDM datatype and a value set  defines a QDM data element, which describes clinical information. The QDM datatype, such as  “Encounter, Performed;” “Procedure, Performed;” or “Medication, Order,” provides context for  the higher-level clinical concept, or QDM category, of clinical information being referenced,  such as encounter, procedure, or medication. Additional data, or attributes, may be necessary to 

Electronic Clinical Quality Measure Logic and Implementation Guidance 10 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

meet the needs of the eCQM. QDM specifies a set of attributes allowable for each QDM  datatype. For example, *dosage* and *supply* are attributes available for “Medication, Order.” All  QDM datatypes include a *code* attribute with a value set or direct reference code. The logic  statements do not explicitly call out the code attribute but invoke it when filtering against the  value set. For detailed descriptions of the QDM data model, including all QDM datatypes and  related attributes, please refer to QDM v5.6.  

This CQL expression also describes the timing elements to determine whether the end of the  specified encounter occurred during the measurement period. By referencing this entire  expression as a CQL definition, in this case called “Inpatient Encounter,” the eCQM can refer to  that definition without repeating all the details. 

The CQL Formatting and Usage Wiki contains additional information regarding the use of CQL  and QDM in Authoring Measures in CQL. Sections 2.2 through 2.5 provide a summary of that  content, focusing on interpreting measures written in CQL.  

**2.2 Understanding CQL Basics** 

CQL is a high-level query language that serves to write expressions that determine *what* data to  return rather than *how* to return them. How to return the data is part of the implementation of an  eCQM, and the measured entity can accomplish it in various ways (for example, by queries in a  database system or map-reduce processing on an Apache Hadoop® \[software utility\] cluster).  

CQL is intentionally silent on many of those details, enabling implementer use of the logic  expressed by CQL queries in a broad variety of implementation environments to achieve the  same result. 

Several basic elements make up CQL expressions: 

• Values: Within CQL, the term value refers to a piece of data of some type. o Examples: The number 5, or the quantity 5 ‘mm\[Hg\]’ 

• Operators: An entity used to perform operations. 

o Examples: “+”, “-“, “and”, “or”, “intersect”, and “union” • Functions: Prebuilt actions that perform calculations, manipulate data, and return results. o Examples: CalculateAge() and First() 

• Identifiers: The names given by a database designer or a system user to database objects. o Examples: “Inpatient Encounter” 

Measure specification logic can combine these basic elements to express criteria and then label them with identifiers so they can either define additional criteria or define a top-level population.  

When the members of an eCQM population are patients, measure developers express the criteria  as a yes or no test to determine whether the patient is in or out of that population segment. As shown in Figure 2.2, the definition of “Initial Population” for CMS2v14, Preventive Care and Screening: Screening for Depression and Follow-Up Plan is: 

Electronic Clinical Quality Measure Logic and Implementation Guidance 11 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

**Figure 2.2. Example with Boolean Return** 

| ◢ Initial Population  “Patient Age 12 Years or Older at Start of Measurement Period”  and exists ( “Qualifying Encounter During Measurement Period” )  |
| :---- |

For this example, the initial population includes patients whose birthdate indicates they were 12  years of age or older at the start of the measurement period, and the patient has a qualifying  encounter.  

In patient-based eCQMs, measure developers define each population, such as the initial  population, denominator, or numerator, by criteria resulting in a Boolean—yes or no—return;  however, there could be other definitions in the eCQM that return lists. “Qualifying  Encounter During Measurement Period” is one such definition, which returns a list of encounter data. In Figure 2.3, the relevant period indicates the start and end times for the  qualifying encounter.  

**Figure 2.3. Example with List Return** 

| ◢ Qualifying Encounter During Measurement Period  ( \[“Encounter, Performed”: “Encounter to Screen for Depression”\]  union \[“Encounter, Performed”: “Physical Therapy Evaluation”\]  union \[“Encounter, Performed”: “Telephone Visits\] ) QualifyingEncounter  where QualifyingEncounter.relevantPeriod during day of “Measurement Period” |
| :---- |

**2.3 Libraries** 

CQL libraries are collections of CQL expression definitions, functions, and other declarations.  Each eCQM contains a primary CQL library defining the criteria used by the populations of the  eCQM. The HQMF document references the CQL library, which contains expressions defining  measure populations.  

Libraries can contain the following:  

• Expression definitions, such as “Inpatient Encounter” 

• Terminologies, such as references to code systems, value sets, direct reference codes, codable concepts, and codes 

• Functions, such as “NormalizeInterval” 

Once an eCQM includes a reference to a library, the eCQM can subsequently reference  components of that library throughout the eCQM. In Figure 2.4, the definition for one of the  denominator exclusions in CMS108v13, Venous Thromboembolism Prophylaxis*,* is  Encounter Less Than 2 Days. 

**Figure 2.4. Use of Libraries in Definitions**

| ◢ Encounter Less Than 2 Days  VTE.”Encounter with Age Range and without VTE Diagnosis or Obstetrical Conditions” QualifyingEncounter  where Global.”LengthInDays” ( QualifyingEncounter.relevantPeriod ) \< 2  |
| :---- |

Electronic Clinical Quality Measure Logic and Implementation Guidance 12 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

This example defines inpatient hospitalizations with a qualifying encounter when the length of  stay is less than two days, referring in the logic to the Global.”LengthInDays” function. The Global in this definition refers to a library used by many of the eCQMs that share definitions and functions such as LengthinDays. eCQMs can use definitions and functions contained in the Venous Thromboembolism (VTE) library related to VTE encounters and  diagnoses, including the VTE.”Encounter with Age Range and without VTE  Diagnosis or Obstetrical Conditions” definition used in this example. 

For more information on libraries, refer to the Using Libraries to Share Logic section (Chapter  2—Author’s Guide) of the CQL specification. 

**2.4 Queries**  

A central construct in CQL is the query, a specific type of expression enabling easy and precise  expression of relationships between data. Queries in CQL are clause based, which means they  use different types of clauses depending on what operations the logic performs on the data.  

The general structure of a CQL query:  

\<source\> \<alias\> 

 \<with or without clauses\> 

 \<where clause\> 

 \<return clause\> 

 \<sort clause\> 

Because all the clauses are optional, the simplest query is just a source and an alias: “Outpatient Encounters” Encounter  

Here, the source is a reference to “Outpatient Encounters,” which is an expression returning a list of encounters. Encounter is the alias. The alias allows reference to the elements of the source anywhere within the query. Because this simple query does not have any  clauses, it simply returns the same result as the source. 

**2.4.1 Where Clause** 

The where keyword introduces a where clause, which enables the user to filter the results of the source, as shown in Figure 2.5 from CMS124v13, Cervical Cancer Screening.  

**Figure 2.5. Where Clause**

| ◢ Qualifying Encounters  ( \[“Encounter, Performed”: “Office Visit”\]   union \[“Encounter, Performed”: “Preventive Care Services Established Office Visit, 18 and Up”\]  union \[“Encounter, Performed”: “Preventive Care Services Initial Office Visit, 18 and Up”\]   union \[“Encounter, Performed”: “Home Healthcare Services”\]   union \[“Encounter, Performed”: “Telephone Visits”\]   union \[“Encounter, Performed”: “Virtual Encounter”\] ) ValidEncounters   where ValidEncounters.relevantPeriod during day of “Measurement Period” |
| :---- |

Electronic Clinical Quality Measure Logic and Implementation Guidance 13 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

This query returns only those encounters from the source whose relevantPeriod is during the Measurement Period. The where clause enables measure developers to specify any condition in terms of the aliases introduced in the query, such as ValidEncounters in this case. Every encounter performed in the union query source evaluates the condition in the where clause, and the result then includes only those encounters for which the condition evaluates to true. 

**2.4.2 Relationships – With and Without Clauses** 

Describing relationships between data is so common in quality measurement that CQL provides  special constructs to make expressing these relationships simple by using with and without keywords. The with keyword can serve to describe cases that measured entities should consider only if a related data item is present. The example query, as shown in Figure 2.6 from  CMS146v13, Appropriate Testing for Pharyngitis*,* limits the emergency department (ED) or  ambulatory encounters to return only those starting three days or less on or before the day that  the measured entity ordered the antibiotic. The such that clause describes the condition of the relationship, which is expressed in terms of the aliases EDOrAmbulatoryVisit for the main source of the query and AntibioticOrdered. 

**Figure 2.6. With Clause Example** 

| ◢ Encounter With Antibiotic Ordered Within Three Days  “Qualifying Encounter” EDOrAmbulatoryVisit   with \[“Medication, Order”: “Antibiotic Medications for Pharyngitis”\] AntibioticOrdered   such that ( start of EDOrAmbulatoryVisit.relevantPeriod ) 3 days or less on or before day of  AntibioticOrdered.authorDateTime |
| :---- |

The without keyword can serve to describe cases that measured entities should consider only if a particular data item is *not* present. As shown in Figure 2.7, the numerator definition in  CMS154v13, Appropriate Treatment for Upper Respiratory Infection (URI) is: 

**Figure 2.7. Without Clause Example** 

| ◢ Numerator  “Encounter with Upper Respiratory Infection” EncounterWithURI   without \[“Medication, Order”: “Antibiotic Medications for Upper Respiratory Infection”\] OrderedAntibiotic  such that OrderedAntibiotic.authorDatetime 3 days or less on or after start of   EncounterWithURI.relevantPeriod   return EncounterWithURI |
| :---- |

This query limits the encounters returned to only those that did *not* have a medication order for  an antibiotic medication for upper respiratory infection. Similar to the with clause, the without clause uses such that to describe the condition of the relationship. 

**2.5 Timing Calculations** 

Assessing the relative timing of events within a patient’s electronic medical record is an essential  part of computing eCQMs. To enable the unambiguous interpretation of the eCQMs, it is  necessary to clearly define computation of time intervals. A simple expression such as “the  treatment must occur within 3 days of the diagnosis” has many possible interpretations, including 

Electronic Clinical Quality Measure Logic and Implementation Guidance 14 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

that the treatment must occur within 72 hours of the diagnosis or that the treatment must happen  within 3 business days of the diagnosis.  

The International Organization for Standardization 8601:2004 defines data elements and  interchange formats for the representation of dates and times, including time intervals. A full list  of definitions related to timing is part of the Clinical Quality Language Specification, Release 1  Mixed Normative/Trial-Use (CQL 1.5). 

To determine the length of time between two dates, CQL provides two approaches: duration, the  number of whole periods between two dates, and difference, the number of period boundaries  crossed between two dates. The expression of each period represents a time unit, such as hours,  days, or months. These approaches provide options to correctly express timing relationships for  implementation of measures. For example, in hospital \- inpatient and hospital \- outpatient  eCQMs, the recommended timing pattern for initial population criteria is an encounter that “ends  during day of “Measurement Period.’” The use of ‘ends’ provides a single point of time  comparison within the measurement period so the receiving systems can provide reports on a  quarterly basis, while ‘day of’ specifies day precision to avoid time zone offset and millisecond  issues. In contrast, dateTime precision would result in applying a time zone offset adjustment,  for example.  

Appendix B of this document includes more information on time intervals and examples, as does  Appendix H of the CQL specification.  

**2.5.1 Duration** 

CQL provides specificity when calculating duration. Conceptually, the calculation is performed considering two dates on a timeline and counting the number of whole periods (for example,  years, days, hours) fitting on the timeline between the two dates. CQL considers this calculation  to be as fine grained as necessary to meet this intent of the measure. Only the available data  limits the precision of the calculation. 

**2.5.2 Difference** 

Difference calculations are achieved by truncating the date or time values at the next level of  precision and then performing the corresponding duration calculation on the truncated values.  

To illustrate the difference: 

Date 1: 2021-12-31  

Date 2: 2022-01-01  

Duration In Years: Years between Date 1 and Date 2 \= 0 

Difference In Years: Difference in Years between Date 1 and Date 2 \= 1  

The Duration In Years expression returns zero because a full year has not passed between the  two dates, but the Difference In Years expression returns 1 because the one-year boundary was  crossed between the two truncated dates––2021 and 2022\. Please note that the difference  boundary at day precision (that is, date) is midnight.

Electronic Clinical Quality Measure Logic and Implementation Guidance 15 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 2\. Clinical Quality Language Measure Logic 

**2.5.3 Intervals** 

CQL supports intervals of numbers and date or time values using a standard mathematical  notation to indicate open and closed. Brackets indicate a closed endpoint, and parentheses  indicate an open endpoint. An open interval boundary excludes the endpoint and a closed  interval boundary includes it. For example, Interval\[5, 10\) includes the point 5 but excludes the point 10\.  

Intervals use a set of comparison operators such as A during B, A overlaps B, or A  includes B. Precise relationships between intervals use natural language timing phrases such as A starts before start B or A starts 1 day or less after end B. 

The unit expressing a time interval or its duration could depend on the necessary level of  accuracy for the purposes of measurement. Measure developers select the time unit to use  according to the level of granularity required to meet the intent of the measure. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 16 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

**3\. Data Elements and Value Sets** 

eCQMs build data elements from the datatypes and attributes in the QDM. Additional reference  material can be found at QDM \- Quality Data Model section of the eCQI Resource Center.  

**3.1 eCQM Data Element Repository**  

The eCQM Data Element Repository (DERep) provides additional clarification for all data  elements associated with published and tested eCQMs used in CMS quality reporting programs,  along with the definitions and clinical focus for each data element. An end user can filter  information by data element, eCQM, QDM attribute, QDM category, QDM datatype, or QDM  entities. 

**3.2 Value Set Location and Tools** 

Value sets are lists of unique coded identifiers with names, called descriptors, for groupings of  clinical and administrative concepts selected from standard code systems. They serve to define a  set of concepts (for example, diabetes or clinical visit), identifying selected populations and  satisfying measure criteria in eCQMs. Value sets can be found at the Value Set Authority Center  (VSAC), which is maintained by the National Library of Medicine (NLM) in collaboration with  CMS. The VSAC provides downloadable access to all official versions of value set content  contained in the eCQM specifications. The value sets used in eCQMs and available in the VSAC  can either directly contain code system members or reference other value sets, as in a grouping  value set. The VSAC also provides value set authoring capabilities for registered value set  authors and updates value set content based on each new version of the underlying code systems,  such as Current Procedural Terminology (CPT), International Classification of Diseases, Tenth  Revision, Clinical Modification (ICD-10-CM), Systematized Nomenclature of Medicine– Clinical Terms (SNOMED CT), or Logical Observation Identifiers Names and Codes (LOINC)  used in value sets.  

The NLM has an application programming interface (API) to the VSAC content in addition to a  web interface. The API documentation is available on the VSAC on the VSAC API Resources page.  

The VSAC also links to downloadable value set content used in current and previous eCQM  release sets. These downloads are in both Excel and sharing value sets-compliant Extensible  Markup Language (XML) for all hospital \- inpatient, hospital \- outpatient, and eligible clinician  value sets.  

The downloadable files include a column indicating the QDM category represented by each  value set. For value sets used exclusively to express QDM attributes, the QDM category column  is blank because more than one QDM category may use an attribute.  

The value set spreadsheets do not include the direct reference codes used in eCQMs. To obtain a  separate listing of those codes, users must select the “Direct Reference Codes Specified within  eCQM HQMF files Published *Month DD, YYYY*.” 

Electronic Clinical Quality Measure Logic and Implementation Guidance 17 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

The VSAC provides three additional resources:  

• The binding parameter specification documents the information used to create the value set expansions made available for an annual release or addendum. The binding parameter specification contains the code system version, the value set definition version, and the expansion profile information used to create each of the value set expansions included in the annual release, sorted by measure and QDM datatype. 

• A list of retired and legacy codes in eCQM value sets published in a value set release. 

• A list of the code system versions used in eCQM value sets published in a value set release. 

Access to value set details in VSAC requires a free Unified Medical Language System® Metathesaurus License. Any use of value sets must be consistent with the licensing requirements  and copyright protections covered by this license.  

**3.3 Direct Reference Codes** 

A direct reference code specifies QDM data elements in measure logic rather than creating  single-code value sets. The use of direct reference codes prevents using an alternative identifier  for a code system concept and eliminates additional implementation work to unpack the value  set. These codes display directly within the measure logic and in the Terminology section of the  HQMF. Some value sets containing only one code, however, are appropriate in the following  circumstances: 

• When authoring a value set, only one code exists but there is a reasonable expectation the measure developer may consider additional codes added to the terminology to represent the intent of the value set. 

• A value set initially contained multiple codes, but all except one was retired by the code system or systems. Because the measure might have to allow look-back, the value set remains valid with only one active code. 

As shown in Figures 3.1 and 3.2, direct reference codes are found in CMS124v13, Cervical  Cancer Screening*,* in which individual codes within the CQL logic reference discharge to home  for hospice care, discharge to healthcare facility for hospice care, hospice care assessment, and a  yes response to an assessment. The measure’s Terminology section lists each of these codes. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 18 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

**Figure 3.1. Direct Reference Codes** 

| ◢ Hospice.Has Hospice Services  exists ( \["Encounter, Performed": "Encounter Inpatient"\] InpatientEncounter   where ( InpatientEncounter.dischargeDisposition \~ "Discharge to home for hospice care  (procedure)"   or InpatientEncounter.dischargeDisposition \~ "Discharge to healthcare facility for hospice care  (procedure)"  )   and InpatientEncounter.relevantPeriod ends during day of "Measurement Period"  )   or exists ( \["Encounter, Performed": "Hospice Encounter"\] HospiceEncounter  where HospiceEncounter.relevantPeriod overlaps day of "Measurement Period"  )   or exists ( \["Assessment, Performed": "Hospice care \[Minimum Data Set\]"\] HospiceAssessment where HospiceAssessment.result \~ "Yes (qualifier value)"   and Global."NormalizeInterval" ( HospiceAssessment.relevantDatetime,   HospiceAssessment.relevantPeriod ) overlaps day of "Measurement Period"  )   or exists ( \["Intervention, Order": "Hospice Care Ambulatory"\] HospiceOrder  where HospiceOrder.authorDatetime during day of "Measurement Period"  )   or exists ( \["Intervention, Performed": "Hospice Care Ambulatory"\] HospicePerformed   where Global."NormalizeInterval" ( HospicePerformed.relevantDatetime,   HospicePerformed.relevantPeriod ) overlaps day of "Measurement Period"   )   or exists ( \["Diagnosis": "Hospice Diagnosis"\] HospiceCareDiagnosis  where HospiceCareDiagnosis.prevalencePeriod overlaps day of "Measurement Period"  ) |
| ----- |

**Figure 3.2. Terminology Aligned with Direct Reference Codes from Figure 3.1** 

| Terminology  • code “Discharge to healthcare facility for hospice care (procedure)” (“SNOMEDCT Code (428371000124100)”)  • code “Discharge to home for hospice care (procedure)” (“SNOMEDCT Code (428361000124107)”) • code “Functional Assessment of Chronic Illness Therapy — Palliative Care Questionnaire (FACIT Pal)” (“LOINC Code (71007-9)”)  • code “Hospice care \[Minimum Data Set\]” (“LOINC Code (45755-6)”)  • code “Yes (qualifier value)” (“SNOMEDCT Code (373066001)”) |
| :---- |

**3.4 QDM Category and Code System** 

CMS’s Measures Management System (MMS) Hub published a recommended vocabularies document with the ONC-Health Information Technology Standards Committee with recommended code systems for each QDM category.4 Measure developers link data elements to  a value set, grouping value set, or a direct reference code, complying with these  recommendations when authoring measures. Downloadable resources of value sets by QDM  datatype are also available on the VSAC on the Download tab.  

Some measures combine different options, or definitions, to express instances of a single QDM  datatype within the logic. As shown in Figure 3.3, qualifying encounters exist in the initial  population for CMS131v13, Diabetes: Eye Exam.  

4 The committee made these recommendations in 2012 and 2015 using program information and language current at  the time, and the recommendations are consistent with the Interoperability Standards Advisory.

Electronic Clinical Quality Measure Logic and Implementation Guidance 19 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

**Figure 3.3. Definition Using Multiple Data Elements to Address Terminology Requirements** 

| ◢ Qualifying Encounters  ( \[“Encounter, Performed”: “Office Visit”\]  union \[“Encounter, Performed”: “Annual Wellness Visit”\]  union \[“Encounter, Performed”: “Preventive Care Services Established Office Visit, 18 and Up”\] union \[“Encounter, Performed”: “Preventive Care Serviced Initial Office Visit, 18 and Up”\] union \[“Encounter, Performed”: “Home Healthcare Services”\]  union \[“Encounter, Performed”: “Ophthalmological Services”\]  union \[“Encounter, Performed”: “Telephone Visits”\] ) ValidEncounters  where ValidEncounters.relevantPeriod during day of “Measurement Period” |
| :---- |

In this example, the seven encounters all use the same QDM datatype    
(Encounter, Performed), and each binds a grouping value set using a single code system and the logic (union) to express these encounter types meet the measure criteria. Together, these seven encounters provide codes covering the recommended code system, SNOMED CT, as  well as the native capture terminologies (formerly referred to as ‘transition code systems’), CPT  and Healthcare Common Procedure Coding System (HCPCS).  

CMS encourages users of the eCQMs updated for 2026 reporting or performance to suggest  additions and deletions to data elements, both within value sets and between code systems. Users  can submit their suggestions via the ASTP/ONC Project Tracking System (Jira) QDM Issue  Tracker. 

**3.5 Drug Representations Used in Value Sets** 

Value sets referring to specific non-vaccine, prescribable medications use generalized drug  concepts, such as RxNorm Semantic Clinical Drugs \[SCDs\]. Health information technology (IT) vendors or measured entities report the drug entities in patient data using the generalized drug  concepts included in the defined value sets. These concur with CMS’s guidance regarding the  preferred use of generalized drug concepts. eCQM implementers should use the relationships  found in RxNorm to support mapping between specific drug entities found in patient records to  those found in the provided value sets. If mapping is conducted, implementers should maintain  documentation in case of a CMS audit. Administered vaccines are currently represented in value  sets using Clinical Vaccine Formulation (CVX) codes.  

**3.6 Discharge Medications** 

The use of “Medication, Discharge” has a very specific meaning in hospital – inpatient eCQMs. This designation refers to medications reconciled in the clinical environment and  documented on the patient’s discharge medication list. “Medication, Discharge” **events are not equivalent to medications that happen to be active at the time of discharge.** Discharge medications represent those medications the patient should be taking in the next  setting of care, generally used for discharges to home. These discharge medications will be the  same as the subsequent home medication list resulting from medication reconciliation in the  ambulatory patient medical record. This reconciled medication list is very likely to be different  from the ambulatory medication list existing before admission to the hospital since the discharge  medication list will account for new clinical considerations identified during the hospitalization. 

Figure 3.4 is an example of measure logic for “Medication, Discharge” in CMS104v13, Discharged on Antithrombotic Therapy, in which the numerator logic contains 

Electronic Clinical Quality Measure Logic and Implementation Guidance 20 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

\[“Medication, Discharge”: “ Antithrombotic Therapy for Ischemic  Stroke”\] and refers to the presence of an antithrombotic therapy on the discharge medication list of the ischemic stroke encounter. 

**Figure 3.4. Discharge Medication Example** 

| ◢ Numerator  TJC.”Ischemic Stroke Encounter” IschemicStrokeEncounter  with \[“Medication, Discharge”: “Antithrombotic Therapy for Ischemic Stroke”\] DischargeAntithrombotic  such that DischargeAntithrombotic.authorDatetime during IschemicStrokeEncounter.relevantPeriod |
| :---- |

Health IT vendors and measured entities generating Quality Reporting Document Architecture  (QRDA) Category I output will have to generate “Medication, Discharge” events for all medications on the discharge list with appropriate time stamps to enable the correct function of  measure logic. 

**3.7 Allergies to Medications and Other Substances** 

Allergy value sets referenced in eCQMs contain appropriate codes from RxNorm aligned with ingredient-level concepts. Users can use RxNorm relationships to link ingredients to the specific  drug entities that could occur in patients’ records. eCQMs generally express reactions that may  occur, such as rash or wheezing, using appropriate SNOMED CT codes representing the  condition.  

This means that value sets used by eCQMs to identify medication allergens for the QDM  datatype “Allergy/Intolerance” use RxNorm ingredient-type concepts. These concepts only identify the ingredient and not the form or strength, and they have the RxNorm term type  consistent with ingredient-level identifiers. If the EHR does not record patient allergy and  intolerance data using an RxNorm ingredient-type concept, measured entities will have to use  RxNorm relationships to identify the correct ingredient concept for the drug concept used in  the EHR.  

eCQM developers can add SNOMED CT drug class concepts to represent medication allergens.  If a drug class concept is appropriate for use in the measure, the following should apply:  

• eCQM specifications will only include SNOMED CT drug class concepts when a general drug class concept is expected to be found in patient records as an indication that the patient is considered allergic to all drugs in the class. 

• Implementers should keep in mind that when using a drug class concept to represent a measure exclusion or exception, the patient should receive no drug in the class for expected therapy. 

• eCQM specifications will include review of all defined drugs in the class when choosing to include the SNOMED CT drug class concept and will define an RxNorm allergy value set with the specific ingredient (IN) and precise ingredient (PIN) term types drug ingredients representing the drug class. 

• Some value sets group both the RxNorm ingredient-type allergy value set and the SNOMED CT drug class allergy value set into one grouping value set referenced by the eCQM.

Electronic Clinical Quality Measure Logic and Implementation Guidance 21 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

• Value sets used to identify nonmedication allergy-inducing entities use concepts found in the SNOMED CT substance semantic types. 

**3.8 Principal Diagnosis in Inpatient Encounters** 

In EHRs, the principal diagnosis is defined as the condition established to be chiefly responsible  for the admission of a patient to the hospital for care according to the Uniform Hospital  Discharge Data Set definition.  

QDM v5.6 adds the attribute *rank* with an integer value. A principal diagnosis is the encounter related billing diagnosis with a *rank* of 1\. For example, CMS108v13: Venous Thromboembolism  Prophylaxis, is depicted in Figure 3.5.  

**Figure 3.5. Principal Diagnosis Example** 

| ◢ Encounter With Principal Diagnosis of Mental Disorder or Stroke  VTE.”Encounter With Age Range and Without VTE Diagnosis or Obstetrical Conditions” Qualifying Encounter where exists ( QualifyingEncounter.diagnoses EncounterDiagnoses   where EncounterDiagnoses.rank \= 1  and ( EncounterDiagnoses.code in “Mental Health Diagnoses”  or EncounterDiagnoses.code in “Hemorrhagic Stroke”  or EncounterDiagnoses.code in “Ischemic Stroke”  )   ) |
| :---- |

eCQM requests for all encounter-related diagnoses, regardless of rank, use the encounter  attribute diagnosis. Note that, by definition, a principal diagnosis must be present at the time of the initiation of the encounter. The attribute diagnosis does not specify the exact timing relationship. Therefore, to express the timing relationship of a specific diagnosis with an  encounter, the eCQM must use the QDM datatype “Diagnosis” to associate with the “Encounter, Performed”, that is, onset at the start or end of the episode of care. The admission or discharge diagnoses recorded by the measured entity should not substitute for the  principal diagnosis unless it is concordant with the principal diagnosis as defined by CMS.  

**3.9 Medical Reason, Patient Reason, System Reason** 

Capturing evidence in EHRs of an intentional decision to refrain from performing an activity and  the rationale can be challenging. Some eCQMs seek to retrieve evidence using the QDM  attribute, *negationRationale*. eCQMs use value sets to specify medical reason, patient reason,  and/or system reason why a particular performance activity did not happen. For example, a  medical reason for exception may include a rare but relevant comorbidity that could not be  anticipated by the measure developer to create an explicit exclusion based on the condition. A  patient reason for exception from denominator criteria may include religious preference.  

If a patient or episode meets a measure’s denominator exception criteria, the measured entity  must translate the specific reason for the exception to a code from the value set or the direct  reference code that is used to specify the measure’s denominator exception criteria. The  measured entity should retain supporting documentation of the specific reason that the patient or  episode met the denominator exception criteria, as CMS may expect the measured entity to  demonstrate relevant justification if audited. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 22 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

For example, CMS2v14: Preventive Care and Screening: Screening for Depression and Follow Up Plan excepts patients from the denominator who are not screened for depression based on a  medical reason, as depicted in Figure 3.6.  

**Figure 3.6. Denominator Exception Example** 

| ◢ Denominator Exceptions  ( exists "Medical or Patient Reason for Not Screening Adolescent for Depression"   and not "Has Adolescent Depression Screening"  )    or ( exists "Medical or Patient Reason for Not Screening Adult for Depression"   and not "Has Adult Depression Screening"   )   ◢ Medical or Patient Reason for Not Screening Adult for Depression  \["Assessment, Not Performed": "Adult depression screening assessment"\] NoAdultScreen   with "Qualifying Encounter During Measurement Period" QualifyingEncounter   such that NoAdultScreen.authorDatetime during QualifyingEncounter.relevantPeriod   where ( NoAdultScreen.negationRationale \~ "Depression screening declined (situation)"    or NoAdultScreen.negationRationale in "Medical Reason"    ) |
| :---- |

If a patient met this measure’s denominator exception criteria of not being screened for  depression based on a medical reason, the measured entity would need to only report a code from  the “Medical Reason” (OID: 2.16.840.1.113883.3.526.3.1007) value set, such as “Procedure not  indicated (situation).” The measured entity should confirm, however, that there is a legitimate  medical reason for exception and should document specific information on the medical reason in  case of a CMS audit. 

**3.10 Activities That Were “Not Done”** 

Measure developers may use a negation attribute to identify situations in which an expected  action did not occur (for example, the measured entity did not place or administer an order or did  not observe a finding for a documented reason). The approach in CQL logic requires negation  against the entire value set by providing a nullFlavor code and the value set object identifier  (OID) in a QRDA Category I file to indicate “not done.”  

Thus, measured entities should not arbitrarily select one specific action referenced by a value set  to indicate the action not taken but should instead indicate they did not take *any* actions  referenced in the value set. This approach applies to all QDM datatypes using *negationRationale* to describe activities “not done for a reason” and when the eCQM specification for “not done”  references a value set instead of a direct reference code. It does not change the expression of  negation in the HQMF, but it does require using an HL7 nullFlavor code instead of a specific  code from the value set associated with these activities in the QRDA Category I file. 

The intent of the nullFlavor in this context is to specify that the measured entity intentionally did  not do *all* the activities in the value set. It is not appropriate for a measured entity to certify that a  clinician did not perform an activity using negation unless the measured entity intentionally did  not order or perform the activity in question and documented a justification.  

Figure 3.7 shows an example of “not done” in CQL from CMS108v13, Venous  Thromboembolism Prophylaxis. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 23 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

**Figure 3.7. Example of “Not Done” in CQL** 

| ◢ No Mechanical or Pharmacological VTE Prophylaxis Due to Patient Refusal  ( “No VTE Prophylaxis Medication Administered or Ordered”   union “No Mechanical VTE Prophylaxis Performed or Ordered” ) NoVTEProphylaxis   where NoVTEProphylaxis.negationRationale in “Patient Refusal”  ◢ No VTE Prophylaxis Medication Administered or Ordered  \[“Medication, Not Administered”: “Low Dose Unfractionated Heparin for VTE Prophylaxis”\]   union \[“Medication, Not Administered”: “Low Molecular Weight Heparin for VTE Prophlyaxis”\]  union \[“Medication, Not Administered”: “Injectable Factor Xa Inhibitor for VTE Prophylaxis”\]  union \[“Medication, Not Administered”: “Warfarin”\]   union \[“Medication, Not Administered”: “Rivaroxaban for VTE Prophylaxis”\]   union \[“Medication, Not Ordered”: “Low Dose Unfractionated Heparin for VTE Prophylaxis”\]  union \[“Medication, Not Ordered”: “Low Molecular Weight Heparin for VTE Prophlyaxis”\]  union \[“Medication, Not Ordered”: “Injectable Factor Xa Inhibitor for VTE Prophylaxis”\]   union \[“Medication, Not Ordered”: “Warfarin”\]   union \[“Medication, Not Ordered”: “Rivaroxaban for VTE Prophylaxis”\] |
| :---- |

Figure 3.8 shows a corresponding QRDA I negation instance example for “Medication, Not  Administered: Low Molecular Weight Heparin for VTE Prophylaxis” due to patient refusal.  

**Figure 3.8. Corresponding QRDA I: Example of a Negation Instance “Not Done”**

| \<\!—Medication, Not Administered”: “Low Molecular Weight Heparin for VTE Prophylaxis” 🡪 \<\!—negationRationale in “Patient Refusal” 🡪  \<substanceAdministration classCode=”SBADM” moodCode=”EVN” negationInd=”true” \> ...  \<consumable\>   \<manufacturedProduct classCode=”MANU”\>   ...   \<manufacturedMaterial\>  \<code nullFlavor=”NA” sdtc:valueSet=”2.16.840.1.113883.3.117.1.7.1.219”\>  \<originalText\>None of value set: Low Molecular Weight Heparin for VTE   Prophylaxis\</originalText\>  \</code\>   \</manufacturedMaterial\>   \</manufacturedProduct\>  \</consumable\>  \<author\>   \<templateId root=”2.16.840.1.113883.10.20.24.3.155” extension=”2019-12-01”/\>  \<time value=”20251101060000”/\>   ...  \</author\>  \<entryRelationship typeCode=”RSON”\>   \<observation classCode=”OBS” moodCode=”EVN”\>   …    \<code code= »77301-0 » codeSystem= »2.16.840.1.113883.6.1 » displayName= »reason »  codeSystemName= »LOINC »/\>   \<value code=”105480006” displayName=”Refusal of treatment by patient (situation)”  codeSystem=”2.16.840.1.113883.6.96” codeSystemName=”SNOMED-CT” xsi:type=”CD”/\>  \</observation\>  \</entryRelationship\>  \</substanceAdministration\> |
| :---- |

Electronic Clinical Quality Measure Logic and Implementation Guidance 24 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

Measured entities using these concepts must have a documented reason for patient exceptions in  their EHRs and must demonstrate the relevant justification if audited by CMS. 

A direct reference code could also serve to specify QDM elements in measure logic rather than  creating value sets. For “not done” eCQM logic specified using value sets, use the same  approach described previously when the item is “not done.” To specify “not done” eCQM logic  using a direct reference code, the measured entity should directly negate the direct reference  code itself. For calendar year 2026 reporting, the 2026 CMS QRDA IG and the HL7 QRDA 1  Category 1 Standard for Trial Use (STU) 5.3 with errata provide additional guidance on how to  use null values to describe activities that were “not done” using value sets, and how to report  “not done” if using a direct reference code. 

**3.11 Entities**  

Entities represent concepts that can serve to specify details about the patient, a person related to  the patient, a practitioner, an organization, or a location. They are not QDM datatypes or  attributes. An eCQM can use these entities to provide further information required for an  individual or organization to meet the eCQM’s criteria.5  

• Patient refers to an individual receiving health care services. 

• Related person is someone involved in the care of a patient but not the direct target of care; this entity includes an identifier and a relationship, (for example, mother to a newborn infant). 

• Practitioner is a person with formal responsibility to provide health care with an ability to reference an identifier, role, qualification, or specialty. 

• Organization is a grouping of people or organizations with a common purpose and includes identifier and datatype attributes. 

• Location is information about a physical place and includes identifier and *locationType* attributes. 

Full definitions of the five entities and their attributes as well as technical details are available in  QDM v5.6. Information on which versions to use in each reporting or performance period are  available at the eCQI Resource Center eCQM Standards and Tools Versions. 

**3.12 Supplemental Value Sets**  

Supplemental value sets are used in eCQMs to capture categories such as race, ethnicity, sex, and  payer that are not essential to the functioning of the measure.  

5 For additional information, go to QDM v5.6.

Electronic Clinical Quality Measure Logic and Implementation Guidance 25 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

**3.12.1 Race and Ethnicity** 

The eCQM specifications limit the reporting of patient race to the Centers for Disease Control  and Prevention’s (CDC’s) value set “Race” (OID: 2.16.840.1.114222.4.11.836): 

**Code Description** 

1002-5 American Indian or Alaska Native 

2028-9 Asian 

2054-5 Black or African American 

2076-8 Native Hawaiian or Other Pacific Islander 

2106-3 White 

2131-1 Other Race 

To report an individual patient with a single race category in QRDA Category I, measured  entities report one of the five U.S. Office of Management and Budget race category codes  (1002-5, 2028-9, 2054-5, 2076-8, and 2106-3) in raceCode. To report an individual patient with  more than one race category, the measured entity reports one race in raceCode and additional  races using sdtc:raceCode. In accordance with the standard, all the race codes placed here are  equivalent in priority. Users should not use QRDA Category I, *Other Race* 2131-1, because null  values are not appropriate to represent missing patient race information, as described below.  

For QRDA Category III files, users should identify a patient with multiple races using raceCode  category 2131-1 *Other Race*. This enables measured entities to report patients with multiple  races in an aggregate document without creating multiple entries for a single patient with  multiple races. Only QRDA Category III files should use the raceCode 2131-1 *Other Race* to  express a raceCode category. 

Similarly, the specifications limit the reporting of ethnicity to the CDC value set “Ethnicity”  (OID: 2.16.840.1.114222.4.11.837):  

**Code Description** 

2135-2 Hispanic or Latino 

2186-5 Not Hispanic or Latino  

The 2024 Health Data, Technology, and Interoperability: Certification Program Updates,  Algorithm Transparency, and Information Sharing (HTI-1) rule requires health IT developers  certified under the Health IT Certification Program to do the following: 

§170.315 (a)(5) *Patient demographics and observations* 

Enable a user to record, change, and access patient demographic and observations  data including race, ethnicity, preferred language, sex…name to use…and date of  birth.  

§170.315 (a)(5)(A) *Race and ethnicity* 

(*1*) Enable each one of a patient’s races to be recorded in accordance with, at a minimum, the standard specified in §170.207(f)(3) and whether a patient 

declines to specify race.

Electronic Clinical Quality Measure Logic and Implementation Guidance 26 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

(*2*) Enable each one of a patient’s ethnicities to be recorded in accordance with, at a minimum, the standard specified in §170.207(f)(3) and whether a patient declines to specify ethnicity. 

Notably, the CDC value sets for race and ethnicity do not contain code(s) for patient declined. To  communicate that a demographic element is unknown or the patient declined to provide the  information for race and ethnicity, use the built-in nullFlavor feature of QRDA. The nullFlavor  feature works for race, ethnicity, preferred language, and other cases in which the QRDA calls  for a value from a value set and allows the nullFlavor as specified by the standard.  

Generally, QRDA represents race as: 

\<raceCode code=”2106-3”    
displayName=”White”codeSystem=”2.16.840.1.113883.6.238”/\>  When the value is unknown, use the nullFlavor UNK for “Unknown:”  

\<raceCode nullFlavor=”UNK”/\>  

When the patient declines to specify, use the nullFlavor ASKU for “Asked but Unknown:”  \<raceCode nullFlavor=”ASKU”/\>  

**3.12.2 Sex** 

eCQMs identify a patient’s sex using SNOMED CT codes from the “Federal Administrative Sex” (OID: 2.16.840.1.113762.1.4.1021.121) value set. For reporting/performance year 2026,  this extensional value set contains the following values:  

**Code Description** 

248152002 Female (finding) 

248153007 Male (finding)  

Please see the 2026 CMS QRDA I IG for more detailed guidance on how to report values for this  supplemental data element. 

**3.13 ICD-9 and ICD-10 Codes in Value Sets** 

**3.13.1 Use of Nonclinical or Administrative Code Systems** 

Specifications for eCQMs include terminologies in value sets derived from clinical vocabulary  standards and those originally specified for administrative purposes, such as billing and payment  or mortality data. As an example, diagnosis value sets contain terms in the standard vocabulary  (SNOMED CT) and the native capture terminologies (ICD-9-CM and ICD-10-CM/PCS). A  complete list of value sets and direct reference codes used in eCQMs is available for download  from the VSAC.  

CMS formally retired the use of the ICD-9-CM code system for billing and reporting purposes.  eCQMs may continue to use ICD-9-CM codes, however, to represent historical data and/or data 

Electronic Clinical Quality Measure Logic and Implementation Guidance 27 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 3\. Data Elements and Value Sets 

permitted by CMS per a consensus-based decision across the eCQM interested parties, federal  agencies, and partners.  

eCQM implementers should carefully review technical release notes and value sets posted to the  eCQI Resource Center to determine the areas in which value set changes might affect their  ability to capture data for the 2026 reporting and performance period and adjust accordingly. 

**3.14 Display of Human-Readable HQMF** 

The measure specification package file contains the human-readable HTML file of an eCQM.  Users can also view the human-readable display directly on the eCQM page for a specific eCQM  on the eCQI Resource Center. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 28 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 4\. eCQM Guidance 

**4\. eCQM Guidance** 

CMS provides eCQM guidance to help users understand and implement eCQMs. This guidance  is available in the human-readable HTML and the eCQM HQMF XML in the measure  specification package zip files located on the eCQI Resource Center. On this site, select one of  the following three criteria to view program-specific measures, organized by reporting period:  

• Eligible Clinician eCQMs 

• Hospital \- Inpatient eCQMs 

• Hospital \- Outpatient eCQMs 

Guidance is available in the eCQM header, in the inline comments in the eCQM logic itself, and  in the technical release notes of each eCQM posted on the eCQI Resource Center. This guidance  is critical to the correct implementation of the eCQM. The technical release notes, which  describe changes to measures between different versions, are available on the eCQI Resource  Center. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 29 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 5\. ASTP/ONC Project Tracking System (Jira) 

**5\. ASTP/ONC Project Tracking System (Jira)** 

CMS contractors manage and respond to eCQM interested parties through the ASTP/ONC Project Tracking System (Jira). This system supports feedback and questions that apply to all  phases of the eCQM life cycle, including development, approval, implementation, and updates  regarding eCQM intent, specifications, certification, standards, and errors. Interested parties  should report issues related to eCQMs on Jira, especially questions about eCQM specifications.  Interested parties can also ask questions or raise issues about eCQM tools and standards in  different projects within Jira. Quality reporting issue trackers found on Jira include the  following:  

• CMS Hybrid Measures—CMS hybrid measure issues and questions 

• CQL Issue Tracker—CQL development, implementation, and standards issues • CYPRESS Issue Tracker—Certification testing tool test cases and implementation issues • eCQM Issue Tracker—eCQM implementation and value sets issues 

• eCQM Known Issues —Implementation information on eCQMs with known technical issues for which a solution is under development but not yet available in a published eCQM specification 

• MADiE Issue Tracker—eCQM development and testing tool issues 

• QDM Issue Tracker—QDM development and implementation issues 

• QRDA Issue Tracker—QRDA implementation issues 

• QDM Known Issues —Guidance for eCQM developers and implementers on interpreting QDM attributes located on the CQL Formatting and Usage Wiki (not in Jira) 

• QRDA Known Issues Dashboard—Implementation information for QRDA IGs or supporting documents with known technical issues for which a solution is under development but not yet published 

• USCDI+ Quality—Feedback on draft USCDI+ Quality data element list 

A user must create an account to submit, watch, or comment on an issue. Before reporting a new  issue, eCQM interested parties should first search the applicable Jira project to determine  whether a similar question has been previously submitted and addressed. If someone previously  reported a similar issue, interested parties can follow the issue and receive updates. 

When reporting a new issue, interested parties should fill out the ticket completely, selecting a  title summarizing the issue, describing the issue in the description field, including the measure  name and version number when applicable, and selecting an appropriate issue type. CMS  encourages interested parties to add attachments—though never any that include protected health  information—to facilitate a quick and accurate response. If interested parties enter insufficient  information, the assignee will be unable to provide a response until the interested party updates  the ticket. If the interested party does not provide clarifying information within 10 business days  of submitting the initial inquiry, the administrator will close the ticket. Once the assignee  provides a solution, the assignee will update the ticket to a status of “Closed.” Please note, there  may be delays in responding to issues requiring feedback from more than one team or assignee. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 30 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 5\. ASTP/ONC Project Tracking System (Jira) 

If a response is insufficient to answer an issue, the interested party may add a comment to the  existing issue and explicitly state how the previous answer did not adequately address the issue,  citing any previous ticket numbers if applicable. It is appropriate to comment regarding a  missing resolution if there has not been any update or response in more than 10 business days  and there is no comment from the assignee explaining the delay or when the resolution will be  available.

Electronic Clinical Quality Measure Logic and Implementation Guidance 31 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services 6\. CMS Quality Program Helpdesks 

**6\. CMS Quality Program Helpdesks** 

Questions regarding CMS quality and value-based purchasing program reporting requirements  can be addressed to the following Helpdesks.  

**Table 6.1. Helpdesk Contact Information for CMS Quality Reporting Programs**

| CMS Quality Reporting Program  | Helpdesk Contact Info |
| ----- | ----- |
| Hospital Inpatient Quality Reporting (IQR) and PPS Exempt Cancer Hospital Quality Reporting (PCHQR) | Hospital Inpatient Support Team  Quality Question and Answer Tool  (844) 472-4477 |
| Hospital Outpatient Quality Reporting (OQR) and  Rural Emergency Hospital Quality Reporting  (REHQR) | Hospital OQR Support  Quality Question and Answer Tool  (866) 800-8756 |
| Making Primary Care (MCP)  | MCP Help Desk  1-888-734-6433, option 6  MCP@cms.hhs.gov |
| Primary Care First (PCF)  | PCF Support  1-888-517-7753  PCF@telligen.com |
| Quality Payment Program (QPP)  | QPP@cms.hhs.gov  (866) 288-8292 |
| Medicare Promoting Interoperability (formerly EHR  Incentive) Programs  QualityNet reporting and data uploads | QNetSupport@cms.hhs.gov  (866) 288-8912 |

Electronic Clinical Quality Measure Logic and Implementation Guidance 32 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Version History 

**Version History**

| Version  | Date  | Author/Owner  | Description of Change |
| :---- | ----- | ----- | ----- |
| 1.13  | May 5, 2017  | CMS/ONC (MITRE)  | • Updated language in Introduction to include Merit based Incentive Payment System Eligible Clinician and broadened from specific quality reporting programs to generic  • Removed tools, resources, and standards references; now referencing the eCQI Resource Center for this information  • Renumbered and updated Table 3, Example Inputs and Results for Overlap |
|  .  | .  | .  | **Section 2:**  • Updated language in subsections 2.1, 2.2, 2.3, 2.4  • Converted table graphic to image, Figure 1 (Sample Measure Item Count), in subsection 2.2 |
|  .  | .  | .  | **Section 3:**  • Modified introductory paragraph in Section 3 • Removed Tables 1 and 2, Eligible Clinician eCQM Types and Versions and Eligible Hospital eCQM Types and Versions |
| .  | .  | .  | **Section 4:**  • Updated language and logic sample in subsections 4.3.1, 4.3.3, 4.6, 5.2, 5.3  • Updated language in subsections 4.3.2, 4.3.4, 4.3.5 |
|  .  | .  | .  | **Section 6:**  • Updated language in subsection 6.1—UHSIK • Updated language in subsection 6.2—QDM Category and Code System  • Updated language in subsection 6.5—Allergies to Medications and Other Substances  • Updated language in subsection 6.6—Principal Diagnosis in Inpatient Encounters  • Updated language in subsection 6.9—Activities That Were “Not Done”  • Updated language in subsection 6.10— Newborn/Gestational Age  • Updated language in subsection 6.11—Source • Updated language in subsection 6.12—Patient Characteristic Birthdate and Patient Characteristic Expired  • Added subsection 6.15.2—The 2016 Value Set Addendum |

Electronic Clinical Quality Measure Logic and Implementation Guidance 33 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Version History 

| Version  | Date  | Author/Owner  | Description of Change |
| :---- | ----- | ----- | ----- |
| 1.13  | May 5, 2017  | CMS/ONC (MITRE)  | **Appendix B**  • Updated table number for Table 3 Time Interval Definitions and Examples  • Updated acronym list |
| 2.0  | May 4, 2018  | CMS (MITRE)  | • Updates and edits resulting in version 2.0 for release |
| 3.0  | May 3, 2019  | CMS   (Mathematica) | • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Revised text based on input from interested parties and external reviewers  • Updated acronym list |
| 4.0  | May 2020  | CMS   (Mathematica) | • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Revised text based on input from interested parties and external reviewers  • Updated acronym list |
| 5.0  | May 2021  | CMS   (Mathematica) | • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Removed measure tables  • Revised text based on input from interested parties and external reviewers  • Added section on hybrid measures, Program Candidate measures, and telehealth information • Added reference to eCQM and QRDA Known Issue trackers |
| 6.0  | May 2022  | CMS   (Mathematica) | • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Revised text based on input from interested parties and external reviewers |
| 7.0  | May 2023  | CMS   (Mathematica) | • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Revised text based on input from interested parties and external reviewers |
| 8.0  | May 2024  | CMS   (Mathematica) | • Added section related to ratio measures • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Revised text based on input from interested parties and reviewers  • Updated acronym list  • Revised all instances of “pre-rulemaking” to “program candidate”  • Made revisions for clarity throughout  • Moved to active voice  • Replaced the term “stakeholders” with “interested parties” |

Electronic Clinical Quality Measure Logic and Implementation Guidance 34 Version 9.0 May 2025  
Centers for Medicare & Medicaid Services Version History 

| Version  | Date  | Author/Owner  | Description of Change |
| :---- | ----- | ----- | ----- |
| 9.0  | May 2025  | CMS   (Mathematica) | • Updated measure examples, figures, hyperlinks, and versions of standards referenced throughout • Revised text based on input from interested parties and reviewers  • Made revisions for clarity throughout  • Updated section on ratio measures to align with MMS Hub resources  • Removed appendix with specific standards and code systems used in updated eCQMs and directed readers to eCQI Resource Center |

Electronic Clinical Quality Measure Logic and Implementation Guidance 35 Version 9.0 May 2025  
Centers for Medicare & Medicaid Services Appendix A. Standards and Code Systems 

**Appendix A. Standards and Code Systems** 

The standards and code systems used in the updated eCQM specifications for the 2026  reporting/performance period can be found on the eCQI Resource Center’s Standards and Tools  Versions Table or in the eCQMs Annual Update Pre-Publication Document for the 2026  Reporting/Performance Period. 

Electronic Clinical Quality Measure Logic and Implementation Guidance 36 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Appendix B. Time Interval Definitions and Examples 

**Appendix B. Time Interval Definitions and Examples** 

**Interval Operators** 

The CQL standard provides a complete set of interval comparison operators (Figure B.1).  

**Figure B.1. Interval Comparison Operators** 

**![][image2]Timing Phrases**   
The CQL standard also supports timing phrases (Figure B.2) to make it easier to express precise  relationships between intervals using natural language. The before and after operators can have a  prefix of starts or ends and a suffix of start or ends. For example: 

Interval X starts before start Interval Y

Electronic Clinical Quality Measure Logic and Implementation Guidance 37 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Appendix B. Time Interval Definitions and Examples 

**Figure B.2. Interval Starts before Start** 

| ![][image3] |
| :---: |

The before and after operators can also take an offset indicating how far away a given  relationship should be. The offset can be absolute, indicating the boundary of the interval must  be on the offset, or it can be relative, indicating the boundary must be at least on the offset  (Figure B.3):  

Interval X starts 3 days before start Interval Y 

Interval X starts 3 days or more before start Interval Y 

**Figure B.3. Interval Starts before Start with Offset** 

| ![][image4] |
| :---: |

You can also specify a range for the boundary relationship using the within…of operator, as  shown in Figure B.4:  

Interval X starts within 3 days of start Interval Y

Electronic Clinical Quality Measure Logic and Implementation Guidance 38 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Appendix B. Time Interval Definitions and Examples 

**Figure B.4. Interval Starts Within**

| ![][image5] |
| :---: |

Electronic Clinical Quality Measure Logic and Implementation Guidance 39 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Appendix B. Time Interval Definitions and Examples 

**Table B.1. Time Interval Definitions and Examples**

| Unit  | CQL definition  | Examples |
| :---- | ----- | ----- |
| Year  | Defined as the duration of any time  interval starting at a certain time of  day, at a certain calendar date of the  calendar year, and ends at:   • The same time of day on the  same calendar date of the next  calendar year, if it exists  OR  • The same time of day on the  immediately following calendar  date of the next calendar year, if  the same calendar date of the  next calendar year does not exist  **Note:** If, in the next calendar year,  the same calendar date does not  exist, the International Organization  for Standardization states the ending  calendar day has to be agreed upon.  CQL uses this convention.  | Month (date 2\) \< month (date 1): Duration (years) \=  year (date 2\) \- year (date 1\) \- 1   **Example 1:**   Date 1: 2012-**03**\-10 22:05:09   Date 2: 2013-**02**\-18 19:10:03   Duration \= year (date 2\) \- year (date 1\) \- 1 \= 2013 \-  2012 \- 1 \= **0 years**  Month (date 2\) \= month (date 1\) and day (date 2\) \>=  day (date 1\)   Duration (years) \= year (date 2\) \- year (date 1\) **Example 2.a:** day (date 1\) \= day (date 2\)   Date 1: 2012-03-**10** 22:05:09   Date 2: 2013-03-**10** 22:05:09   Duration \= year (date 2\) \- year (date 1\) \= 2013 \- 2012  \= **1 year  Note*:*** Time of day is important in this calculation. If  the time of day of date 2 was less than the time of day  for date 1, the duration of the time interval would be 0  years according to the definition. |
|  .  | .  | **Example 2.b:** day (date 2\) \> day (date 1\)   Date 1: 2012-03-10 22:05:09   Date 2: 2013-03-20 04:01:30   Duration \= year (date 2\) \- year (date 1\) \= 2013 \- 2012  \= **1 year**  Month (date 2\) \= month (date 1\) and day (date 2\) \<  day (date 1\)   Duration (years) \= year (date 2\) \- year (date 1\) \- 1  **Example 3.a:**   Date 1: 2012-02-29   Date 2: 2014-02-28   Duration \= year (date 2\) \- year (date 1\) \- 1 \= 2014 \-  2012 \- 1 \= **1 year** |
| .  | .  | Month (date 2\) \> month (date 1\)   Duration (years) \= year (date 2\) \- year (date 1\) **Example 4.a:**   Date 1: 2012-*03*\-10 11:16:02   Date 2: 2013-*08*\-15 21:34:16   Duration \= year (date 2\) \- year (date 1\) \= 2013 \- 2012  \- **1 year  Example 4.b:**   Date 1: 2012-*02*\-29 10:18:56   Date 2: 2014-*03*\-01 19:02:34   Duration \= year (date 2\) \- year (date 1\) \= 2014 \- 2012  \= **2 years  Note*:*** Because there was no February 29 in 2014, the  number of years can only change when the date  reaches March 1, the first date in 2014 that surpasses  the month and day of date 1 (February 29). |

Electronic Clinical Quality Measure Logic and Implementation Guidance 40 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Appendix B. Time Interval Definitions and Examples 

| Unit  | CQL definition  | Examples |
| :---- | ----- | ----- |
| Month  | Defined as the duration of any time  interval that starts at a certain time of  day at a certain calendar day of the  calendar month and ends at:  The same time of day at the same  calendar day of the ending calendar  month, if it exists  OR  The same time of day at the   immediately following calendar date  of the ending calendar month, if the  same calendar date of the ending  month in the ending year does not  exist  **Note:** If, in the next calendar year,  the same calendar date does not  exist, the International Organization  for Standardization states that the  ending calendar day has to be  agreed upon. CQL uses this   convention.  | Day (date 2\) \>= day (date 1\)   Duration (months) \= (year (date 2\) \- year (date 1)) \* 12  \+ (month (date 2\) \- month (date 1))  **Example 1.a:**   Date 1: 2012-03-01 14:05:45   Date 2: 2012-03-31 23:01:49   Duration \= (year (date 2\) \- year (date 1)) \* 12 \+ (month  (date 2\) \- (month (date 1))   \= (2012 \- 2012\) \* 12 \+ (3 \- 3\) \= **0 months  Example 1.b:**   Date 1: 2012-03-10 22:05:09   Date 2: 2013-06-30 13:00:23   Duration \= (year (date 2\) \- year (date 1)) \* 12 \+ (month  (date 2\) \- (month date 1))   \= (2013 \- 2012\) \* 12 \+ (6 \- 3\) \= 12 \+ 3 \= **15 months** Day (day 2\) \< day (date 1\)   Duration (months) \= (year (date 2\) \- year (date 1)) \* 12  \+ (month (date 2\) \- month (date 1)) \- 1   **Example 2:**   Date 1: 2012-03-10 22:05:09   Date 2: 2013-01-09 07:19:33   Duration \= (year (date 2\) \- year (date 1)) \* 12 \+ (month  (date 2\) \- month (date 1)) – 1   \= (2013 \- 2012\) \* 12 \+ (1 \- 3\) \- 1 \= 12 \- 2 \- 1 \= **9  months**  |
| Weeks  | Defined as a duration of any time  interval starting at a certain time of  day at a certain calendar day at a  certain calendar week and ends at  the same time of day at the same  calendar day of the ending calendar  week. In other words, a complete  week is always seven days long. | Duration \= \[date 2 \- date 1 (days)\] / 7  **Example 1:**   Date 1: 2012-03-10 22:05:09   Date 2: 2012-03-20 07:19:33   Duration \= \[\# days (month (date 1)) \- day (date 1\) \+ \#  days (month (date 1\) \+ 1\) \+ \#days (month (date 1\) \+  2\) \+ ... \+ \# days (month (date 2\) \- 1\) \+ day (date 2)\] / 7  \= (20 \- 10\) / 7 \= 10 / 7 \= **1 week** |
| Days  | Defined as a duration of any time  interval starting at a certain calendar  day and ends at the next calendar  day (1 second to 23 hours, 59  minutes, and 59 seconds).  The duration in days between two  dates will generally be given by  subtracting the start calendar date  from the end calendar date,   respecting the time of day between  the two dates. | Time (date 2\) \< time (date 1\)   Duration \= \[date 2 \- date 1 (days)\] \- 1   **Example 1:**   Date 1: 2012-01-31 12:30:00   Date 2: 2012-02-01 09:00:00   Duration \= 02-01 \- 01-31 \- 1 \= **0 days**  Time (date 2\) \>= time (date 1\)   Duration \= date 2 \- date 1 (days)  **Example 2:**   Date 1: 2012-01-31 12:30:00   Date 2: 2012-02-01 14:00:00   Duration \= 02-01 \- 01-31 \= **1 day** |

Electronic Clinical Quality Measure Logic and Implementation Guidance 41 Version 9.0 May 2025  
Centers for Medicare & Medicaid Services Appendix B. Time Interval Definitions and Examples 

| Unit  | CQL definition  | Examples |
| :---- | ----- | ----- |
| Hours  | Each hour is 60 minutes.  The duration in hours between two  dates is the number of minutes  between the two dates divided by  60\. The unit truncates the result. | **Example 1: **  Date 1: 2012-03-01 03:10:00   Date 2: 2012-03-01 05:09:00  Duration \= **1 hour  Example 2:**   Date 1: 2012-02-29 23:10:00   Date 2: 2012-03-01 00:10:00   Duration \= **1 hour  Example 3:**   Date 1: 2012-03-01 03:10   Date 2: 2012-03-01 04:00   Duration \= **0 hours** |
| Minutes  | Each minute is 60 seconds. The  duration in minutes between two  dates is the number of seconds   between the two dates divided by  60\. The unit truncates the result. | **Example 1: **  Date 1: 2012-03-01 03:10:00   Date 2: 2012-03-01 05:20:00   Duration \= **130 minutes  Example 2:**   Date 1: 2012-02-29 23:10:00   Date 2: 2012-03-01 00:20:00   Duration \= **70 minutes** |

Electronic Clinical Quality Measure Logic and Implementation Guidance 42 Version 9.0 May 2025  
Centers for Medicare & Medicaid Services Acronyms 

**Acronyms** 

**API** application programming interface 

**ASTP** Assistant Secretary for Technology Policy 

**CAD** coronary artery disease 

**CDC** Centers for Disease Control and Prevention 

**CDCREC** Centers for Disease Control and Prevention Race and Ethnicity Code Set **CDT** Current Dental Terminology 

**CM** Clinical Modification 

**CMS** Centers for Medicare & Medicaid Services 

**CPT** Current Procedural Terminology 

**CQL** Clinical Quality Language 

**CVX** Clinical Vaccine Formulation 

**DENEX** denominator exclusion 

**DENOM** denominator 

**DERep** Data Element Repository 

**DEXCEP** denominator exception 

**EC** eligible clinician 

**eCQI** Electronic Clinical Quality Improvement 

**eCQM** electronic clinical quality measure 

**ED** emergency department 

**EHR** electronic health record 

**HCPCS** Healthcare Common Procedure Coding System 

**HHS** Department of Health and Human Services 

**HL7** Health Level Seven International 

**HQMF** Health Quality Measure Format 

**HTML** Hypertext Markup Language 

**HSLOC** Healthcare Service Location Codes 

**ICD** International Classification of Diseases 

**IG** implementation guide 

**IN** ingredient (RxNorm term type) 

**IP** initial population

Electronic Clinical Quality Measure Logic and Implementation Guidance 43 Version 9.0 May 2025   
Centers for Medicare & Medicaid Services Acronyms 

**IPSD** index prescription start date 

**IQR** Inpatient Quality Reporting 

**ISO** International Organization for Standardization 

**LVEF** left ventricular ejection fraction 

**LOINC** Logical Observation Identifiers Names and Codes **MADiE** Measure Authoring Development Integrated Environment **MI** myocardial infarction 

**MIPS** Merit-based Incentive Payment System 

**MMS** Measures Management System 

**MSRPOPL** Measure Population 

**MSRPOPLEX** Measure Population Exclusion 

**NHSN** National Healthcare Safety Network (CDC) 

**NLM** National Library of Medicine 

**NUMER** numerator 

**NUMEX** numerator exclusion 

**OID** object identifier 

**ONC** Office of the National Coordinator for Health Information Technology **OQR** Outpatient Quality Reporting 

**PIN** precise ingredient (RxNorm term type) 

**PCS** Procedure Coding System 

**QDM** Quality Data Model 

**QPP** Quality Payment Program 

**QRDA** Quality Reporting Document Architecture 

**SNOMED CT** Systematized Nomenclature of Medicine Clinical Terms **SOP** Source of Payment 

**VSAC** Value Set Authority Center 

**XML** Extensible Markup Language

Electronic Clinical Quality Measure Logic and Implementation Guidance 44 Version 9.0 May 2025 

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAOsAAABRCAYAAAAzW1HGAAA0nElEQVR4Xu3dd0AUR98H8AMUDlCpYmKNmljiY4ommt6L0eNAiti7xq7RJBpL7LEBV6iCIKJiw4qiKCoKYqF3EOm9XKPe0Xznu8cp7qGAYMmb++Pjyd7tzOzs/nZmtjIePnzIUPn3yisUMWLTijVi0oq15Iq6eF5O6L7MMfDtpY5XPoRlDpe/XuZ42fy9pW6zQf/HHcu1TG236bDsnKDLODsv8umnw7K/Atqm9pHaLPs4bVNOvgLTlCNhmto3APm7jqhlmnJlFDa3mmnKq9Iy41c8wuaKyW9KmiJ55IC2qV08+YzTHWcbLGdH8rU9oc2y8wIdFmePzji7XVrm3NXQw8p17lKnKxZkWb6Wu/zhcofAAf+cDDUEssy6WPbo9GJ1oNfR/xdKE1ReLzfjczp5X00wPBgY3x/Md10c9fXvPhO/+v3oH/DWJBdbsnH7kAC7ASR4EkjwZJDgKCZ/VzSqJ8HzUJPNp2iZOT4kf5PA41TJ8Uo12Q7ZWmxeEpBADSXOaLI5bjByqfeuuZyATfPsLy5vNIewmcu5NAbI/79e43Z91N2kvGEKd5LyBt9Nyn/rSXmUO0n5/clnf3zK/y//W+GO/DuSTv7/IDAyc/gc7qWv53EufSt3kU3yHEf+b0axv2g+bJ7H92SZP4GvV/m8981v3r1lNbUMoNfpv5XSBJXXiypYVcGqoDRB5cUTV8rUUnIEOnAxMqP7H+5Bw393D2LB92uO/2pgwefoWTmdBx1zhwgSUAUkuGRyPNL95NQTCDZ0SR/K/8+VghabL9Yy5aLbGqdj5hAA/1t40PuDXz13fTDfYwFM2nKS5XAm4nP+mfChlLMRPS6EpzOrZLWdGmkQ6oQa1NTWKS3D60ZGyqgob5VUXvaGhgYG0H/7b6U0QaVjBURkdjtxM/l/03b4sWHMX8fXv7/4kCcJpmAgLV0WCUARCbQ6CpuH4HtIWsNGCEYupiFIMU7MI7+/S747BT2sHbdP3eX/67SdfmNhnXfw8KCY7J5BMVl6ofG5nYBeptaqkxVogLTktG516RkjqeB0L0rp6b7EsCb+JxOc+ox8tsXHtDTeIZA2ccaE5KNbXRmuAfRy/VcpTVBpnSJhOSWrSKJ5Oz7faKNX8Iew4UDw9MGz3TlvTnIOBl0zfiYJsgrS4tWCNtUaKgKRgr/RUlYC+X+BNotz18TGxQsGzXZb8+mao7/sPn53KOw6EWYiKKvWFJZXa4CkQkqVp6GhVk6azaiTZqvVybI7k8+uUJMVblyZtrVPZc7Wj6Eie9sv5HN6Ve7m5SCIGLxdEP62myDibV8QRg4MKo/tG10W82YiSCL00sSR+jniSIMiEIUblIrCDSuISoWy6K6y8piuUqhohfLoblUknXIQytMSiyP0CxvlSSL10iTRxkmAsogiBwSRch1v5FCWbLa+KmfLfCDLYlaZvfUTadHGflAnzTEkdOql2epQU5FL6qZeaR3+2yhNUGkdVbCqgvVlU5qgoiyzpIxx/naqDizgXR06fuOZib0nOdqCDosTSIIsm6huRI0jmwYkurWYLv+OW63NskvXYdn7A9PSdpf5Rt+p1lvPjYKTISnGl8IyNO9nixhAL0tVxW11meiiVnUJzwTK7tuMkKTasCTJ7IUgumO0U3jPyIcEQKAgzDhWzrBAHGFQTkgb1ZJgeCiJ7EYhwVFHyIgqSriBUBBmlC24R+alGN0sDTO+VHrX5ASI7hp6Cu8YOZLPfyA1uM+G3GuD/ypLmforSFKmz6fcnzobKtImmzWn/MFkS3j0+5RplOx7Py3JvNZ7Q+Fdk7+h9HZ3R1GEgVtZhN5xEIYbXCTlDCHLmAAk0LPJcpQqCMOMMshyh5FlPAPFwT04kiSrBZWpVj+ANG/7QJnIX6dGGKgO9Dp+XSlN+K/CAQooElVoHAu+r7/N5/b70H+a89IeE50PksBKBaapvUSbOs9ItYj0VhJjzHoyppSSzwLoOdnlTv+prpwVTlenwLaDIcN8rybq5ZeWd6KIyhn19bWM+ppi9UaaVSUnDCpybAdTMld9J4wYvlQc/a4zCCLe9BeGGyUJwwxLKKRVQvCRjbkBEHhlUV0ReGitCkAc2T1JEvNOqCByqC8l4l378owZKytzd05rNIYYWZ2/vR+Icvd0l2bw9cl4VbeRFtGpTlqoBvWyAka9tIDRIMunVFcXM2oqBUp1+rxkNdUMWVURxsxy0kJSP/kMki/yVkNZyCeTfHYBaaazQbVwW4/KnJ1DKbk7f6jK2zFZnGKxCoSRQ51EUUMulsf1DgfSmieSukooi9LzA0nM0F0F0SNtqvJ2D6ksudANyHrQaGioUSrbq6Q04b9CWlNLCYzM1LP1vTfiu9XHlgBp9Y4wTbkppBWsAUUrqdME1VriQJD8YFA1+UzvOp5/EsZtOLl+zf6gb87cSukJ4kqpWtN8G+rLGbKya0yZ4EwfKE+d/k3FfatF4jBDBxBF6l8jG1IqadmqQRShT1o+g4di0hIC+a6OfAolUXqZQAI2RBI32Ls8dfwuECdZz6vI3v69THj+PangfE+QSW4z6cv/X1Qny1cHqfB8D5nA7z3xg1ljoTzF4o/y5JFuZMd3XRzb7QqUhhl5l6Wyl0ky15kAPa1XQWnCf4UqWP97VMH6L1BX38Aoq5R2AudT93ovdQ60emf2Pg8gXdY4TTa/TH7gR37whx6YQH5XAySQ840nOJ//eOnBlbDW88aXN6IzjTOLxJ0B+TXUyxj1dWWdQZh/zKgqn/+lKPHnBSCJe/egOEovlHRTiymR+lVkI2kgXTMKurTkexKkBhlQFmtyXRDef68gZtRCqMzZOLYye+OQuvK7RpTqDJ26mpJ/zbjrdVVfK1FDXcpEF3tAZfamz8vTJs0Th68cAPTfvwpKE/6/CI5I1AXPgNgPLTafWaPN4gQCCbhS4lEQNg3MR62m/PpXYaPgUcsP/WO19cz3cPR6Ynf6RQK1lcnaMvH1QSBOXWQtTvpmJwnAG0Bav3yMI8nnQyDTHgrDjEhgGopASMZOgrtGJ8tjP90AoiTT8dXFuwdJS0/oA47s0pdN5b9JacK/Da5QQfAcuRKlCZsO3fr0w8Xe/zDH/BNOMeWi1axvehCoaYupjcBkke9Z9qXQxYxz5p3pTrNWut0YCglZpdpV0hrSWsrVxJ3VqC5we7MiztoaRHGfu4ij+oSRVlFAiTCsaWwhKSQwa0hXtUgQ1j2QEv7mtvL0mablWX8NhVrJ1W61FcmdGmrKGUBfPhUVBaUJ/zaqYFX5r1Ca8G+A4DwblKALs3f7faltyrMlgZYCTPntW0rdXMXf1DlPNldGAjQF/jfPw+3HVYfMDgTEdgXFRQa1lUkaUF3q8EZFxrKfSu712AWCMKM7pPtKjTOpsWaEAbq29aII/XJKuEGCJP4LH1HCj4ugPGn5yOqSQ9o11aEMoC+LikprKU14XUXfL1aDTd7BQ0YtObieBGREoyp6q6k0BmXz6nFNLZhY8lwX8wPG3YrPMQZxeTWVfp2sUA2qCvf3kyR+O0cU0es4CMKNskjLWEOC9CEgOHFElow/c0EY0ftERcaKhdWFnh9CbUWUQUO9POBVVDqS0oTXiai8Wo0EVE/2xpPTdNi8a8DEFUBNLkZoNjjlFybggve8ofP2H1/ieMX0cni6PtTVyS87q6tK0YGqfKf3yhPHrBRHGt6k4GKCCMMGRXCi1RRH6ZdKok2uUeI/31hd6PypTHxZFxoaXv87UlT+f1Ca8DpRBauKymNKE161jOxShtPZiI9g+Pz9XC1TbgZuHXtacDYJ0MZbyDgh5ptOL9t3KbY/lFVKqXOQ9XWZlKoiz8HixHG/iyO6XwNJpJ4Ql+k16eaScah+IQnSk1D+4PtZ0hy7QbWVMUygl1dF5WVRmvCyVclqGPdzBV1hjr2/pZ4F35+MMSuguYsUFMGpzbLHEVzcrZLXb9pe143eIaMh8n6BVl19PUNYkq4OUtGVnuXpy+cII968AqTlFJOArBeEofU0bCDj0VphuEG2OHqIN5SnTjeXlh4zaairUKe8ZteHqvx3KU14GQrLyylHQ5KMPl9xcAkJODwNAWqbC85HASpvQetIkMZ+/bvPMthx7F7fAlGFWkN9HQNkOX5a4tRfv4m7YOgBonCDXBHpylIXI8jhKG6J4K7xMZDEjLWuLtljUl9TpAb0sqqovC6UJrwMqmBVUWk7pQkvUrWsVm2L783eb1hz/gYmm5Pe9IIFeoA2BimeL1RlYuN8FRY6XDa/FJ7ZRXEhfkNdubqs5EjvstiRC0F01yhCGGYkJeNPjEOpc6Gk61tWfK/3DZDcnzm3qnRPrzpZjgY01MmUyqmi8jpSmvAinL2d2hO+XOmzibSM2fKLFpTvZlFqRdm8KkNrh1Mr91776UGuQBOQXl1tqlpE0J/DQZIweg9aT6F8/EkdKCItaYMkqlsqJeFdTlWh3cjS4vjOQC+bisq/hdKEjuQflqr/9e9HftMx4z0ARStKD86mQUqCWWpo7XgGFnIvfZ1TLJEHaHVuJyhLXzVKHNPXWxSpLwR5C2r8UBShLwVxVK9bkuRx86Tia92hvk6g6tqq/L+gNKEjqYJVRaXjKE1or/u5AibptlqDrjk/mow3WxqTokuMB4nVdp/gFLDC5eqYtHyRBiC9+uoHGtXZf30qudfjKIgj9SsU50QbL1ooE0X1P1GRvfYnqJdmadHLpKLy/4HShOeRm1+gBryj14f1nOzsx2x8eNizA5S6b7Rel81N/HLVkSlw/V6aNtKrl+aqQdmD34eLo/ofJy2mpMmYFAeNygThPT2gIuOPUXWyvA4di+LAVWV1TVPqzVBrRP2mpqaOQk9L5d+hSiqjr3PA+m26vp/4Xip7uU/7V5rQVscDY7oNn+/xNzCpm7WVg5MWqHjKXzF8tdJnY2BYur4irfraCkZV0eF+BffecAVhuMGjVpQErAgkcZ/srcpcPaSmOo8B9PK0VVJOqUZQTLbeHPuL78Fcu4vjhsxzW6rN4uwBHZbdAW2W3RlS9gAFsgwBTFO7U6DDst2vzdqzbdRCt3lA5v9hntulAbcT87QhvaTtDxK7ny1QD03K15HL6yD5OnEZJZ2Ant+LkJhZ0lm5DM8rXyc5R9hhT8PIKS5TJ+um629OgUNgrt2lMcbsTb92GcfZqm3KdZWzPaTNsj2pwyLrnNBl2Z8kfx9ismw9QMvUfufgaW5LZ9tfZMMce//3A8LTDRIyijSAnmdHUJrQVqpgVQVrc1TBqpxveylNaA3cTwqTdlwYYWDhcJ1svBiXPvXg0eNuL7fewJIfuPP4nQ9BWlNHvY+kpiKOCWVxI5aSLm6W4hwpgpSMUWWSuBEnitK2fwQNdWXPtdIU7z0RV0jV17lfHTBqsdcS6G7jdIKMl1MIvKIQsBzNPGK0RY2PJqWuTy4iwqHXNGeHsWuP/+JyMUYPFDcSPMuUrWe/0GTzY0GLzU3pCJpsXsrQuft3QIm46oVsTArZheLOJlYObppUvsplaavOZo4xE7ae+4CeT2vVk/XuGRBrPH7TqYnQb5rbfiYZfpF1JWikeLQsfZ22guLZXXglJifNyIp/HD5ZemDR3gtRvYuFFQzoiHfuKE1oya2ULOY3fx5eAiggCkwPTnqgkiAthDFrjy8OSyt8NL6sk5WoVWev/0oUYRACwnC0osY4T1oLZbH9QsozVn9TJyto1xHdoNjsHr+sOzETSDnwpjU81xcVrVTejta4/OSTmwc9J7nw7U+Hf1IkquwM9LLClG3nfqbeeUrQ02sPkl4FTN1x7hd6nh1pnr2/KeqYnv/z0jLjV9psPfcJPZ9nKS2rYrj4Rb4L/Wa4cUl5irAe5OtCOY+Ohm2L7GjKSACfhHVeN7/LLJK060YQpQnPEp6Sb9hrkssBssB4yl8NvYB02tTeihvGPx3+MeCWN6RTmxeiC6L4L9aJwg3ETY/uUk/8S/p5CdSU39Wjl6G1bifmdgPLLWeXM834yWQjrYOXsaKeRb4SeZK3Z3scgx1Hbo+Wymqf2Bm9qGB93BpwY87cftCVXmft9SBfpA16Fny8OEsp/+fV1mC9HZHG/PL3I382Bih6OS8lQJujaBRI4Fb0nuJ6esvhW8OBXubWUJrwLKpgbT9VsLadKljllCbQ1ZIx1t8nbg8GvQnOofL+uXyl0wsmLxx1SqYGhs3f73r+7gMDxXixoaGcUV1ycKgo3OiaHG5VM8a5UhmURvQ6Vp27bWBDfS0D6GVpjfzCYvUZm32/0zHjxgGzhQsxnubxhk1VOEmDgwevQTEJItKl5ZQ0Kpd///h1GvS0moNyQXcb5xsYTzVdhhcVrAok34Z+01x2ZBYK1YFeh88Dp7u+WHFoOZAAqaPn2R6tDdYLd9P0QZdtf44pH4c+dTttqvF3GHeWk/nwIrFM8v8wwp/J5pylmHIukd/EkM88IN/hJdVPjHPp6TaXB5PNL4bptv5j6eVvidIEujUeN0YwqSfUc1OQGb0QdFiIwbPdf4e0AhF19VFDXaUalD+YYCqMMMho2pKSgC0QxH+6EKSVsdTvn0didmkn6Ddh90YmdaCo5QpsDubBxqwtXyFkxdg7sTactJqx68II2Hw4tL9fWGqPqZxzA2H6Hv+Ppu++YGlswdsB2iz7O0zqHHPzY2J5+lypiSXHDgIjM4zoy/Kig7VxGYWL3YM+BRyAoZehrc6FpvYlLfYDOeU826M1wZqUKWAaWzkdgubqvTmPA41T9t4Crx2z7S+N9LuTpg8343K0bsbmdLoRl6MB5O9ON2OzmV5X43vAjN3+o39Yc2IOWV4fOfsCRcPQ3PIz5fmUfLb04HK4GpnZjb4MLVGaAIqjvav3Xf9Gx5ST+7QC0BeaFFaw0uWqteKCAaRVWRbRWZxothLw9D/SkuKiBtyyVi+KfCtUnLrr3fr6agbQy9FasWlFXQZMd3MDxR6VXsaWNFmGyhGLD+zk+UaYgLhCqobeBT1PurJKGSUxo1hz55HQD4bM8eBDt/H8HG15b4TS1YJ//3f3IDNhWZU60NOBFx2sgGXVNePeghvxedTFKM+rrEqmpm/F4+Lh6EDPq71aClZsq18uOzS3tcMzBQQXDJ/jsalIUN7mg5g1tfWMImGlOvCO3zOcteu8Vf9pey8DKQde34kdswx6T3I+sdbtxiDFBRX0tFpDaQKoglUVrG2hCtZXGKyrPYJGgjaLk9HSht+4gVMXOSx1uMyur3/cpaqV3u5cEjF4q/xB10Y1grDuGJ/WiaIG74ea8oju9LzbKiGzqHP/6W4OCFKgl6+1yHKgcis/+NVrfq6grM0r7mncLsX2HzTH03nwbI8j4HEh4i36b+heRrCCYgfy3arDq4SiSqVytNYEW9/3SN2X09PvKC0Fa1J2iSaTzbun2OHS538axc5l5BJvbnmVrEMuFhGWVXeGJbzAMaRO/H/+y3c6ZBZJmj1N1xZKE44EJfbVYXMToKUFb9xzlK1wDhwLZA9HbeQN9dWdQJTwzjb5M3eNqfOnwnADqTj67e2y8hgtoOfdFtKaOsr7Cw/Mwt60rSuKthx1Q+bt2wg5RZJmW7v2IK0z3lDeGejfNeclBmsjTgH/VFibLzq4k5SvBd3M+b4Ienr6HaWlYOWfiRjMNOW1ua60Te0oJKiq35ru5rPtcOj3viFJxlAgrNCkv9OoLXBAtbAxjfak09QTf9xJyuvWa8reCy11Zx6tZBanir3p9NymadTXlWtI7k9aD6QVrW1sTauhPOKdNXXV4R1y9YxnQHw/IHvUTHr52ooEe9KeM3f0gJ7Pq/CyglUBgfb2THe/lFyhFtDL0xwclGJvOjUeSLB32AUQzWkpWFlbTn6jyeYrzdcWjQ1PrRablyPHvfDD2hM7rbefnQgHAuPfuxyebiAUVzGAXoaXgfpHRloo+HHNsb9QYPqC0CmCeeicffb3c4WPWgu86rDs/oQponCDCmg8LVNTFDlkLdSXh3VIV6OiWsYYsdh7A+DOHXr52oLsVR/2mua6qYKMI4Ce16vwsoMVsN6n7vKbDTWtGKOHpxRo67A40fC8PZrWailYRy/z+U6rncGq8Li3QfU4UC/UOJjAKbrUt6buvQIfLTqw1fbEXRb32L03ISqtBD0npbJ1JOofVbCqglUVrHKvfbDO4p8dCqRAQnrh6bAQXdicMAgMT3/iKpjqbLtB4gi94sdjVBxMeseuviyqE9Azf14+N+53JUGGCx7i2ruhkDSkG7yCP6Xn8Sq1MVgbdFh26IZi2PLUoUtrkO5fJqxzChhIL1NTZeXVDNb6EyuwowN6Ogpk3chAp73laiFYHagxK7eKPt+LoAjkxmXH0WRc/1tGxr6h3cx422fsuvAtnAt9oJ8vqlAqa3swEjJLNQwsHfcBxi70wtGRSqlcxLv8PSgSqauIZ4Ik4o2T8odnN17wEDUwoKYsvM0nf1uywCPofVJWnKJ57qO/ChifbDwc+iY9j1epLcGqjbudxtm6arM4EqB/3xaKHpPxBKcDQfcznnowzON8dF+yHaQrNlx6OvK0OA1Mlr07oIz079uipWC9djtZk6zH0GeV50VR5NkIF9PgJhEpWRdJ/Wa4bd/kHTIMpDVPXlL6PBhLnIJIi8ophZYWFIXpMcHxUHahuBMgARz1KkudZgHiCH0p1aKGGRZDVdGxt+kZdoS3Z+yd/LQrhFpLUcG4DS2zSKxLz+NValuwchq+WOAwY8gsty3Q3sAAEoiyH9cct6qsrWOAolx5xeVq8O68/U4k36fmQ9Utyz7yl9VHPwZsN/TftEVLwYrz4Dbb/cbhLhegz/+qoB5wpBl6TnQ+vOf43RH5Aoka0JehNVTBqgpWJapg7RgdHqx9bXgrmVSiypnRaZnyqn9zDvzqiYqqTNAgQXoN5GNVo3ph3Ki10J6rkp7lnWl7F3dUsJI0QrOKJO26gqejtTVYp+86P/PgjXgjYFIXoCv/rs3MuIn8s+EmoCjXIt6VkUC6nM+8AIJsS1LW2mMT1u8PHgYvOliholqmMWHr2aWgRXY2HVIHHUgeuNziEYs8V0J+aVmbr4Nn6LDsLis2XHoGTTOivjfn3wuIznziPFzx/UXDRBH6dYBzqqJwg5TqVG8joGfWUd6Z7taBwWofmvn/IFgVdzbN2O33Hdko2jV2ladr3zBklrszpOeJNVJyBMweE10uwNO2FUWddrNwOH8nJb/zJu9bw+BlBCvg3C9YbTvLJvUSj3wbKaX5KlAB23ir5rD5Hl45xRId+jI8C4K1pKVgVRz16znRiUdPQBz35Yon7keN/fCf+royBtB/21EGTvecrCgTvaytpVhmeTdY0oWex6v0PMGqmFdQVqXec/JebnvqRp4utd4rYOruC9+t2HvVguwEqoH+WwUckQXzf05QgfWyg1UBp+AOXIk3GfGr5zogdZTKpB6J274dfEci9Vo3YqHXuvxCIQPoy9AcVbCqgrVZqmB9sZ4rWFtTkWSs2gBm285ObjpzbXGEmvCWiZfivKokUq9OnLbyI3omHW3zoVvvaTc+GJxe1rbSYvNzth+88wY9j1epPcEKF++l9dBm2eMcNNUNpM/TWk124gldzDjUteJP26mTja/BxNrJGSQV8hdYv6pgVVBc7HP05v0uM7afNR0wzfUQkMBNY7J5uOcYp1oe3UBOz/dFku8MeUVbD9wYBPSyN4dBT6Q52tRtZ5y6DQdCfm46c03Z3U7CMKMARbDiBVFVhW4G9Ew62uHQtC5k48Bd+zHtrWQc+ZzMOfcFPY9Xqb3BKqupZcy1vWCmaOno87RVazZmkk+2742kd0BRjlcdrHQlwjLK9ehMk7UeN74jY9u/gWlqH4CdNlmGGi02D+feW1zejkC23/rPVh1ZAlWteGA4usF4PMUzC6cI1r8PhPzYdObGYA181LJGdQuvE0R0yIX6z1JaVs0wmei2AZ51w0FroLvYY6LDtvbcZ9jR2huskJYv0Xh75j53aG8dtQSt6he/+fxJv8PkdQtWOsUBqVJJVeeg6CzDncfufvrxikNz4e1Z+zy6WjiGkHVQJKdoiZ8dK23R2Lo6Qr6gosXTOapgVQVru6mC9fm0OViZLLsWL9Mi3YIGmLbnlHXTmWXl99QEEUbej4I1pltIQ31Nh98P2px1bjf7gBabm0Evb1uR7k8G9/g9E6Dn8yp0RLDC0aDEXkCCNfNZ6/d5KbYbUoZ4v3tPXicOrzJYc4olnW/GZHcF+netFZqQp+VzLbE3WG0++/27cz03km3llhynqr11Kp+f7woFgooW44ZhON5xk+KaUHpiCszGhyP3nMjf3HTmhgYZQ5Iw+k9BmOFDEEfqJcgKD7X5ZO/zqCZ9fBi+fN8MUnHtuvkcd+68O8djD+SVlHXYDQeAc5+l4ir1UnFlJ2jNk9k7Kljx1A6Yuuv8ZFI3HXrPaWOQUtfB/srxn9jccr3sYFVsEx7+MYN6TnQ+1tWMdxuWOl/7skBY0e4en6SimnEzPlsb/nAP+lzPnHddu/FtFPQytwbqZdgCrw0gqWz5jh3GjiO33ycbuwjoidF1teCHhiZkPRGM5Vkb/yeKMKiR0y+rKnDvTc/kRYrLKtEYMMXFluxMaoFe5tYiFYe33lV/sGD/b/mC8navWDwjGRxPhb/dlcV17TLW9hg4nL3XX1T17JuXOypYFeIeFGl2t3Q42p4dGh02tB7WjpcgNUfQ7EUlLzNYr0Wn63y6zGsBaLM4BbgiT7G8ZLuoNLLieaz1DP4Q0gvE7V6/cOxavFE3S6dYoJe5NUj3t2qFU+D3QE+7OapgbaQK1rZRBesrCNa80vJOb81wPwAtVSoW2nrj8a8U3SskIJXc7SQKN8ADu3F98ENx8s+kS1THAHpmL0rUg8IufabvdQZm48ub6WVvyeMVy5H1m+rivsnr5iAor5Kp1zV5CFxzamtrGeR3anAuJFl3ju2F0YqDOySYClGvj9Jncx6MWXfcAi/IAnpa0NHBCruO3RnAbHzn0PPUDx3Jt3oB1/8ToOel8KKDNV9YRZnHCxyOF3FrU8Mh+ZCInga1DlgcEfSc5Ozz82af74OisrpClfT5bl9LzhFodp+49ybQ82sJytjdxvlycrZAG+hpN4f6Z6PbxbdAy5SbiwdI0RNumoGWGff0zfg8LcC8DQ31jIrUeT8ACdZyUbT+VUHhOSbQM3uR4rMEneDjpQfWkg3yuR/yLV9O6gkB4ka+rE1nFs629f8JZtpfHj53j/+QWXaXPgQybcxPqzzna43d7Qbk9/G4OuVZ+TPZPBlZURy4FpWp9ITHFxGsuDPl+z+PzgdSRtwQrpRWa2G5upjaud3PK2EAPS+FFx2sZCetDYaWDnj5U6vzaNwpN5B6SIL+0/Y6kvVo43U5bvCthFzjRtrBibkaIQlyofG5nW8l5HUNTch9ExbzAr4ZMNPNm6RVC/Q8nkaxXSDW/vYKfo++TM9C/SMUCimfLz84GU+Je+aGRvaorA0nbUDxJPc6aW4nEEa+y5VE6lWXRH9vDXjMCz3DF61SWqMxadeF73XY3FhQHG6nL0drNVmx1KV2Wmy+iMl2EJJPMZBpUnyPAAf6/E/DVLw+g+yVPS8+/+szWhusEJlaoAs9p+w9/zx1otguSLnzZv69bzA9fboXHawK50LTDT5ceABDoQpo7bIplke+c+bUkvUpYFKXJnJSSTAFdzbjn9dqxGTzL2njNZ5sbi6l8bnA9DSfBfWgzeZmw1/7rn/Xmtd/NkX9owrWp1MFa9O8VMH6yoNVQVxerf79H0eXMVu4TI1kWgwLXPz+13T+uupUHdHt7v5k/JoF1UVH+tEzfFluJeR0ha9XHV2kTXV37OuhrRXcUR5vGNTGXqE/nu8LU9bvGx37oPS53yLXlmBV2Olze5AWmydQlIee5tMwGx+k/r95Xn8Iyp59kAxeVrBCsaiS8Yf79RHQe4rrWW3qhWGP65ye5svSZIdQ1WuS8+ktB272B3r5W0NpgkBSpfH5Cp8/gPmUm3ipPQRhbO0YfuHugycylqXt7COKMAgGcWSPK5Ulx17Yfa2tIZRUMxzORpuM+evYbNBm2d0kZadaQ3mLqLx8Halxr40dRRFoj9nmbLlm/2dR9ws6A7288KKDFT2i9xd4rUZrAvQ0n4b0upLB+VR0q67/fpnB2lRmoVhzw4HgT7qwOe5A6qiwsSeDdfFCg1cRnI3bVpUOy/4ifP3niZ9uJ+W36pnMT6M0AUSkhYVvV+yfxGTZFzxt74SVoGvGj9196NYQqJLWUhcB1BT46YM4ZshxSdyIvfXSnE5Az+dlUhzBDo1J11jNOzVwxK97l8Kg2e6ntVmc+0w2r4xCPd3/cbf2saZ7SXkQ0lB3cGAHRwgMrBzDYeRCLzfWGt+xDv5R3UAqqyXleHb3Z8rWM5+RLtk9IL2C6GcheUVN23meTU+jJfGZxV0MLflHgdlMunRaprwo1t++VtD0FSnPssk7ZCCQ+aPo6bWFFtvhzsQtZ9t0MAaKRFUUx/OxXWfaXmKNWHTACbqaO4SRZS4hO0MpaDcG8rPXd/PrnNl4ypD0VHA7YdqAmW4XYMTSg7+5nIscWFBarg4tnVFoDaUJoApWVbDSqYK1+XX+yoNVoaSkhLHzyK3RpFB3gBRK6bIqBCz57gFM333hx7omG2JtZWwXSdK4tRUPFn0J9PRfB0Iy9gqJyzE+fiPpPZi554LlDFv/NW8vPsgBHRbnEFkJfmQ5r1FYdhFM6tPeT4574Ie/Tu4m8/0Gs3b7jSFDg0Ex6cU6QM+vNZKyStSD43N0ISQ+uwU5uilZpc+1I4x+UKgJymkqC47P1UkrEKoDPZ2nySoQqQHKSE+vbXJ0E7MErc63JfdSCpmkbt9e7HjlWyDrbfGU3f7byPaNLvMZCum6knUd9pj9JbJTP0d4wcjlR+xm7vFfM2vPeRs4FBj/ISlnjyJxpRrQ8+wIShPoML5JyCzRg48We/9N9iB4sgQ1ZlXup3PLBs32XE020m6A+RsaatSri44YAz3t1xnuL4SySqm6pFKmIamUdmqk2fiJaYRMvTWvhFR5vaFHSNalmnxdP1rfWNcKinWuDngpGj2NF01pwrPgLXFr910faWzt6AMkOKnTPE1bWjKt7q3p7ldhy6FbnxYLW771R0VFpWVKE55FFawqKq+O0oTWKBRWdIYVjoGf97Bx8iV9/ErAbXZNB+Rk8C75bPkhV1u/22/D0970raKi0jKlCW2VUShWX70v6FPoYcNzY8rfgk5dnSMfx1LnnErgrSkunD3HQtr0Xhm8Me5ieIbGzdhcdSAtNSM9T8woLimmBARFMe7cL3xinuzicsal8BxKbHLeE99dTyhkRGTnP/o74kEJ40ZiASMqs0QNkjOLqOmK5/AG3EvViMt48qKFG7F5aqWicgZU19QywrIepxedWqAWEJbeKb9YxABMi03LYSSkZlNuxeUo9TRScvMZxeIqimJaWkEh5U50MuN2bOajebBsV+5lMJIzhZTrUVnqFU3GT4WiKkbAjTBG1P3H1+xGp5WoXSRlyispYwCmkXEZw/9WjBrU1dUxCgWVjIS8IkpAWKY6HjQWHp9MSSmWp3Xnfi6FXv7s7OxnHuHOKpLX6aO/s7IYecUVjND4HEqOWExND4hKoSTmiBmJ2aVqeCIIKOa7GhlJCUsvoP4WiisoAffSNO4kZD3RENxJzFHDwU7FAc/AiDhGVHoR5VZiIaNIUsW4EZWtBvTyKtxOzNPILhCqQX6JhJFO1kdBQQEljqyzyJQ8RlBstjoUlkqodROdnEwpk8rvTw2JiKEE3IhjxGVmUuV5VKawFHVS1xqA+1nvpRU9KkvMg3yGpErK8A9PVof4TIGaUgHbo0BQrn4kKKnf5B1+S0DP0uFu4yFt6gIE0DXj3N55LLwn0OdXwGFuCEsuMBw02+MfE2uHXXrmfA4MmLT32zcmOnv0ney4G0Yv3PudgQVvk8W2E6MA83+/+vj72ix7KzCycNjwzux9+wbNcudAFzOOtcEEPpf83w4MLB1ndjHnzTOZ6OwCb9g4uq33Cv5w7PrTP4CRJc++x0Snv8+EZqkD0n9jhsM3fabt9YSBM93+GbvpxAdzuRc/gTes+Q5v2jhu6zNRzvtqvPGv/IssE0uOG3Q157mNXX+CpXjyHtLbcOD6O1/8dngFlFXJ1NILJYyBcz2Xw3erHAeOWOi5fNDsfc7w5mSXzTrj7L7oPsHZHXraONoNmOlmv4gbMBp8g1OMNcfZOk/YfqbfEt71QWBkxXcwmeCwrddkly2QmFWqvdojaKTWLzv+gDKyoRwNSjYynOTqDvrjea7vLfBaO33Hqbeg+2RHt0Gz9u3pOdHBHFBm7Mg8/KP6Qq8JXNvBc11XhKXnaYJiPWYUiDWhxxTXtRfD0/QV06fv9P2SvfHMpBGLvUZAz6nO+8my7dJm238D3W2cd/ab6sIbONPdHkYs8pyRlFWq2W8a7zd4f6WX+cnbKV36TnXeAKS8e7pb8fmbvYKHQuSDwi5643n2d5LytAF5Gk9y3Dz6jwM/wRuTnaYNmOnuYmLF3w2DZ7tvPhqUSDUiilN849cd/aa7JWd3r4kOG2HV3mujekx22t9rEn8XLHEO+FrfymkHqVtH6DPZhddvkuunBpYcdzhyLbFnbFpRZz3TXcsbsYyt7Vc/yBd1hq9/PzKp27idDt2tHfbAgKnOE/StHLYNnr2PByTNZWPX+X5pYM21hzdtnGyVAqU9VMGqClZVsP5LgpUuJr2kk+3R2x998/uRzcA05d5hsnmSvtPcb0DUg8Jmn3mUkCVQB53xjl59Z+5zKRRVdPrb5/qb0HPK1q+02NyUcX8f7wFegfFdSXf7io4ZLxWOXk8cOmq5108LeP6WcPxmsq6uhUvo138eZYP11rN9yE4j6Yvl3t/C+oPBXfpMdt7907pTG+D9X/fzyfdOWmyHczD+79Ps2XsuDWj6MDDDhXbWZDmuwSzuRSOLDacGkWVLhj0n7n1eJa1R6zd1Lw+0LZw8FvAv/0bSxG1cJ3/ZcGY2Gd9fwbtZAOltOhDMIt/lg/fVhAHr9wUPJnnnwI9rTpiOWLjfrd8Ul02w2Omynun6U190NncOAZ/Q+M6stcfwAG4q/yVOV4ZrmjslbT986z1Da6e7MG697yzcCL+Cc34APMgVdBo214NH8ssC13Mx3Y4FJfbpNH5vNix1vPIDWba0Dxc6fAakfh+YrvcdutzdXxdQ5lrSdTY2XTcbyE7xBvnN3pCEXH1QrMdN+68bA9lhp331+7G5cQ/yO4GRtYN/75ke+4ynOP8M5PvISf+c7Tl146EeQNKL4Z2JGL7n6L2eoMnmJ07YesZ6wDTXU8DadPq3wXPctr45weE4XI/JYp4PTjLmnb6nB0sdAn4idSFa73nDFFAWExtnP5t/LsyAvlOcN5htPm0b/aCoE/Se5u7czdLJp1BYoYaL64HJ5vuZ/eFmMXP76d6w1ffuxzosTrLF5pPG4HExxqCTufO9JU5XzYDU463+M90nk3xxwUXYxH/85u48ETZswDSXzbDU6epibZbdBastZ38GTbZDysYDp3qRYQkTTP/yHEh2wtdHLztkBXP/OdpNfzzXx3Kz7wxY5hjYXylQXgTFhp6SI+iy6WDIyBGLDq6Ct6Y6r/GLTFY66BSXUdwVyEpK2HzoFlXZCqZ8n4+wUWuZ2nNgsVPgV6QSuF3MuA6gb+l07635HrPcLkSzAfPoWe8NmbbH/2fYuO+qMZk/hczjDf1muloYW/F2Glo5BgHZIwexNp4Z0WfGvjWgZeZY+M4cD7vI1KLOgPSoYDXl3gctU7vdXcbuWU4CKwN8b6VRFyjMsr0wFbTM+DHdJ7mu0mHZxwFppQNHLDvMxnk6xbm6iVvOzdBmcSKh12TnXSZWjn8qVvqPq48vGrHA01WHZRcEpOxbekx2RLCGAv9sXOfjN5IMyMrPBxJoYxCs5NNci80vBPJ9n6Z1eOh6onHvyU6upGdwExbyA6wRrJrmziXwho3T2e6WvD22vnd7AamnLLIhuRtZ2VkC0pDV1jI+/8OLDbj7aPhc9982eAV/AQcux72L32z0Dh4IZP4AbXOH0Hd+df4JyN/nybq71NmK9zMwce0uy95h5Fzn0UCCNZp3NmJYGRlXg9Ek92MW/1xY23/63lPwwWz+rJ42/Ns/rj0+GxTLVUu2MSABvLPLOK5t32luRyG3pEyd9JrOkECdCX2mUsFqp5hv6g4/C7KtRRy8Gq+lCFY9C6e1ZN0X95vkYgt/Ol3+lNR/ESkbD0j9fkN2MoFv2DifAhLI5zwvxfZ4b6H3KtAx51/+ctXhpUtdr3wMy12uLSY7ND/dcXZbgazrI8Ky6kdPrCgSVWhi/Wqb2p0EQ0v+ou4TXBZqmjkVQI9JrvuUAutlIq0nIyNfpDTAj8soYQKpwGCLzWeWYlpOcbkafLXFa5S+lWOsvV+4BlyNztDUMbWz872ZbAh9prjuJ3tFgTsJVMC83ejByubGWu84+w5cjUxXGzLLfTdprc+BNot7f/uBkCEHA5P0YOpOvx/J7/Mcz0W8AUgPwaplyj8HLmejNP/ae2002WAzwTf0vjEe2vXuXM9NoG3OP/XTH4fxpr5QOV7K727Xf2l6oKHnJMdNY/46xgbSohX2snHebTie7wSjlh/e9u4st71D5+1bCQcDY9RN1536jATqLTgVmtlpkePpT0hrnQzLHC+/3xis32mbcrNhsWPgZ8iHdL80YNgSt0U6Y/e464yzPQs9Jjr7b/G51b+zuUsBkJ3EZVKPHJ9rCf2AbGT3f+cH9D16LVkDkJbT+YjuZKNNgl9W+8wky55BgvAQRKYWUE8UXMS/8h28MdFxehcLp1tk+a/CmL+OmpMNMlLH1N4CyPoK237git7OI3cMQUfesg47F5amC10snW7bbD5pqQhW1sbTCwfP8Tz96fJDOwA9mfJKmdpG9xsDQMeUc1nH3NaOBFM2nLyZPLQxWKmWtfdUFypYa+vq1WDMOt8N3SwcfR7klz46sHgjNttw/f6bX5CdVwwsdbpirsuyT1zreZMJ/vdSdTubOYaQusHjco6SnfLNgwGJ+n/suzkAyHpON7ZyOHQ/u1QLEKxk2c+TncciIHV8K5kMRxTbfGB0li4J1uvmW0//DN5XYtVvxOYYzN9zdjQYWjunKwXQy6QKVlWwqoJVvs2/9sH6NIpL/Wbu8vtOfzz/7qhl3lvemuK6G0bN329Kuk1FQ+Z47oftPrfNyApxT8oW6ELE/QKDnlPcAhTd4OS8UnVdC+eIabv9rUDeDbZP7T/V5SR8vPzgb93MuXxNMy4XSIU5kYo+RYLWE96f77mFBMH+wNgsLUD5us+0t9E25d2CYnGlVlx2SWejiY7bYchs9yMfL/HGPP6w/eiddxc4XFmpxeacgg8XHVhFAjLSOzhaH5Ber0lOPrbH7nQFsoK9f3e78f67c/b9DZpsHrrr+40s+bdg1LKDbqSrbEqWORNGLTmwtbs1/wJr7YnxcPhqfK/OZk4Z671uDR27zncRGFs7Bn+y9MBfPSc6bQNSLt9j1+K0tx0O6Q1kw8l/a7KbqWLMOmKhNwvBR8o1H8jGWDhk9j6fb1cdng4oMxkn6iuCtfdEx4OkW3eJlCcR/vIMGUAt1xS3xbDO8/qP4zb4zuk/3201rD0QNIKUgQS37QwK6Qa/P9/r4Pu/eo4FJosTPXDGXtfuExyOUmwcd4ZEZzAHkEAF042nV3tfjvuABHUIfPjr/t3D53nsIOtuP9jYXv48JqtIvauFgz98ttJnTVdrl6s2//jNg75TXXZ2t3EK/miRly3gWIKt7z3qtR+xsXEU0g11JXW2Uffnbe6w1M53LNmplAyb5+kFWw+HsjTMXSIm7ThvBaQOQwfP9Vybmi/UAJMp7kc+n2c3Q7FNy7vBdv6OF6JMgDne1nfAdNeDZNveDLoWfEuynoP7TXE+B6OXHtxmYMl3/mTpwS1gZME7+X/AEUn6EVPjaAAAAABJRU5ErkJggg==>

[image2]: <data:image/png;base64,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>

[image3]: <data:image/png;base64,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>

[image4]: <data:image/png;base64,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>

[image5]: <data:image/png;base64,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>