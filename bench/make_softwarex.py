"""Builds the SoftwareX Original Software Publication (Word) from the measured results and the journal's template.

    python bench/make_softwarex.py [data-dir] [template.docx] [file-name]
        defaults: D:/nungil-bench, <data-dir>/reference/template/softwarex-osp-template.docx, Nungil-softwarex.docx

Run analyze.py first. The document is written into a copy of the template, so it keeps the template's page
set-up, styles and metadata table. Limits enforced here: abstract about 100 words, at most six keywords, at
most six figures, and 4,000 counted words (abstract, running text, captions); the six-page limit on the main
text is checked afterwards by exporting a copy without tables and figures.
"""
import copy
import json
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

import make_article as base
import make_manuscript as ms
from make_article import NAMES, ORDER, pct

if not (ms.REPO / "app" / "build.gradle").exists():          # scripts extracted from the branch live outside the repository
    ms.REPO = Path("D:/Ml-Scanner")

ACCESSED = "accessed 3 October 2026"
FIGURES = ["architecture", "tradeoff", "threshold", "ablation", "gallery", "screens"]
TABLES = ["metadata", "research", "detection", "faces"]
WORD_LIMIT, ABSTRACT_WORDS, FONT = 4000, 115, "Arial"


def F(key):
    return f"Fig. {FIGURES.index(key) + 1}"


def T(key):
    return f"Table {TABLES.index(key) + 1}"


# Elsevier numbered reference style, in order of first citation.
REFS = {
    "who2026": f"World Health Organization, Blindness and vision impairment, 2026. https://www.who.int/news-room/fact-sheets/detail/blindness-and-visual-impairment ({ACCESSED}).",
    "bourne2021": "GBD 2019 Blindness and Vision Impairment Collaborators, Trends in prevalence of blindness and distance and near vision impairment over 30 years: an analysis for the Global Burden of Disease Study, Lancet Glob. Health 9 (2021) e130–e143. https://doi.org/10.1016/S2214-109X(20)30425-3.",
    "khan2021": "A. Khan, S. Khusro, An insight into smartphone-based assistive solutions for visually impaired and blind people: issues, challenges and opportunities, Univ. Access Inf. Soc. 20 (2021) 265–298. https://doi.org/10.1007/s10209-020-00733-8.",
    "kuriakose2022": "B. Kuriakose, R. Shrestha, F.E. Sandnes, Tools and technologies for blind and visually impaired navigation support: a review, IETE Tech. Rev. 39 (2022) 3–18. https://doi.org/10.1080/02564602.2020.1819893.",
    "budrionis2022": "A. Budrionis, D. Plikynas, P. Daniušis, A. Indrulionis, Smartphone-based computer vision travelling aids for blind and visually impaired individuals: a systematic review, Assist. Technol. 34 (2022) 178–194. https://doi.org/10.1080/10400435.2020.1743381.",
    "manship": "S. Ngnamsie Njimbouom, K. Lee, J.-D. Kim, MANSHIP: mobile-based assistive notification service for hearing-impaired people using a hybrid deep learning model, Technol. Health Care 33 (2025) 1787–1799. https://doi.org/10.1177/09287329241309702.",
    "seeingai": f"Microsoft, Seeing AI user manual, revision D, 2024. https://www.microsoft.com/en-us/garage/wp-content/uploads/2025/04/Seeing-AI-User-Manual.pdf ({ACCESSED}).",
    "lookout": f"Google, Lookout help. https://support.google.com/accessibility/android/answer/9031274 ({ACCESSED}).",
    "bme": f"Be My Eyes. https://www.bemyeyes.com/ ({ACCESSED}).",
    "envision": f"Envision, Envision app. https://www.letsenvision.com/app ({ACCESSED}).",
    "lin2017": "B.-S. Lin, C.-C. Lee, P.-Y. Chiang, Simple smartphone-based guiding system for visually impaired people, Sensors 17 (2017) 1371. https://doi.org/10.3390/s17061371.",
    "bai2019": "J. Bai, Z. Liu, Y. Lin, Y. Li, S. Lian, D. Liu, Wearable travel aid for environment perception and navigation of visually impaired people, Electronics 8 (2019) 697. https://doi.org/10.3390/electronics8060697.",
    "joshi2020": "R.C. Joshi, S. Yadav, M.K. Dutta, C.M. Travieso-Gonzalez, Efficient multi-object detection and smart navigation using artificial intelligence for visually impaired people, Entropy 22 (2020) 941. https://doi.org/10.3390/e22090941.",
    "see2022": "A.R. See, B.G. Sasing, W.D. Advincula, A smartphone-based mobility assistant using depth imaging for visually impaired and blind, Appl. Sci. 12 (2022) 2802. https://doi.org/10.3390/app12062802.",
    "kuriakose2023": "B. Kuriakose, R. Shrestha, F.E. Sandnes, DeepNAVI: a deep learning based smartphone navigation assistant for people with visual impairments, Expert Syst. Appl. 212 (2023) 118720. https://doi.org/10.1016/j.eswa.2022.118720.",
    "zhang2024": "K. Zhang, Y. Wang, S. Shi, Q. Wang, C. Wang, S. Liu, Improved yolov5 algorithm combined with depth camera and embedded system for blind indoor visual assistance, Sci. Rep. 14 (2024) 23000. https://doi.org/10.1038/s41598-024-74416-2.",
    "chen2024": "Z. Chen, Z. Liu, K. Wang, K. Wang, S. Lian, A large vision-language model based environment perception system for visually impaired people, in: 2024 IEEE/RSJ International Conference on Intelligent Robots and Systems (IROS), IEEE, 2024, pp. 221–228. https://doi.org/10.1109/IROS58592.2024.10801813.",
    "gao2025": "Y. Gao, D. Wu, J. Song, X. Zhang, B. Hou, H. Liu, et al., A wearable obstacle avoidance device for visually impaired individuals with cross-modal learning, Nat. Commun. 16 (2025) 2857. https://doi.org/10.1038/s41467-025-58085-x.",
    "mekonnen2025": "F.Y. Mekonnen, M.F. Al Bataineh, D. Abu Abdoun, A. Serag, K.T. Tamiru, W. Abula, et al., Development of a fully autonomous offline assistive system for visually impaired individuals: a privacy-first approach, Sensors 25 (2025) 6006. https://doi.org/10.3390/s25196006.",
    "walkvlm": "Z. Yuan, T. Zhang, Y. Zhu, J. Zhang, Y. Deng, Z. Jia, et al., WalkVLM: aid visually impaired people walking by vision language model, in: 2025 IEEE/CVF International Conference on Computer Vision (ICCV), IEEE, 2025, pp. 9845–9854. https://doi.org/10.1109/ICCV51701.2025.00918.",
    "alhammad2026": "N. Alhammad, A. Alsubaie, R. Alomair, F. Alamro, M. Alammar, Munir: a multimodal smart-glasses system for enhancing human–computer interaction for visually impaired individuals, Sensors 26 (2026) 3950. https://doi.org/10.3390/s26123950.",
    "lin2026": "H.-Y. Lin, Y.-H. Fan, C.-C. Chang, Multimodal navigation system for visually impaired users using environmental perception and vision-language models, Sensors 26 (2026) 3045. https://doi.org/10.3390/s26103045.",
    "visionaid": "C.-G. Florea, S. Spînu, VisionAId: an offline-first multimodal Android assistant for people with visual impairment, featuring personalized object retrieval, arXiv preprint (2026). https://arxiv.org/abs/2607.02371.",
    "mediapipe": "C. Lugaresi, J. Tang, H. Nash, C. McClanahan, E. Uboweja, M. Hays, et al., MediaPipe: a framework for building perception pipelines, arXiv preprint (2019). https://arxiv.org/abs/1906.08172.",
    "efficientdet": "M. Tan, R. Pang, Q.V. Le, EfficientDet: scalable and efficient object detection, in: 2020 IEEE/CVF Conference on Computer Vision and Pattern Recognition (CVPR), IEEE, 2020, pp. 10778–10787. https://doi.org/10.1109/CVPR42600.2020.01079.",
    "ssd": "W. Liu, D. Anguelov, D. Erhan, C. Szegedy, S. Reed, C.-Y. Fu, A.C. Berg, SSD: single shot multibox detector, in: Computer Vision – ECCV 2016, Springer, Cham, 2016, pp. 21–37. https://doi.org/10.1007/978-3-319-46448-0_2.",
    "mobilenetv2": "M. Sandler, A. Howard, M. Zhu, A. Zhmoginov, L.-C. Chen, MobileNetV2: inverted residuals and linear bottlenecks, in: 2018 IEEE/CVF Conference on Computer Vision and Pattern Recognition, IEEE, 2018, pp. 4510–4520. https://doi.org/10.1109/CVPR.2018.00474.",
    "efficientnet": "M. Tan, Q.V. Le, EfficientNet: rethinking model scaling for convolutional neural networks, arXiv preprint (2019). https://arxiv.org/abs/1905.11946.",
    "mlkit": f"Google, ML Kit. https://developers.google.com/ml-kit ({ACCESSED}).",
    "facenet": "F. Schroff, D. Kalenichenko, J. Philbin, FaceNet: a unified embedding for face recognition and clustering, in: 2015 IEEE Conference on Computer Vision and Pattern Recognition (CVPR), IEEE, 2015, pp. 815–823. https://doi.org/10.1109/CVPR.2015.7298682.",
    "facenet_android": f"shubham0204, FaceRecognition_With_FaceNet_Android, GitHub repository. https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android ({ACCESSED}).",
    "mobilenetv3": "A. Howard, M. Sandler, B. Chen, W. Wang, L.-C. Chen, M. Tan, et al., Searching for MobileNetV3, in: 2019 IEEE/CVF International Conference on Computer Vision (ICCV), IEEE, 2019, pp. 1314–1324. https://doi.org/10.1109/ICCV.2019.00140.",
    "arcoredepth": "J. Valentin, A. Kowdle, J.T. Barron, N. Wadhwa, M. Dzitsiuk, M. Schoenberg, et al., Depth from motion for smartphone AR, ACM Trans. Graph. 37 (2018) 1–19. https://doi.org/10.1145/3272127.3275041.",
    "coco": "T.-Y. Lin, M. Maire, S. Belongie, J. Hays, P. Perona, D. Ramanan, P. Dollár, C.L. Zitnick, Microsoft COCO: common objects in context, in: Computer Vision – ECCV 2014, Springer, Cham, 2014, pp. 740–755. https://doi.org/10.1007/978-3-319-10602-1_48.",
    "lfw": "G.B. Huang, M. Ramesh, T. Berg, E. Learned-Miller, Labeled faces in the wild: a database for studying face recognition in unconstrained environments, Technical Report 07-49, University of Massachusetts, Amherst, 2007.",
    "efron1979": "B. Efron, Bootstrap methods: another look at the jackknife, Ann. Stat. 7 (1979) 1–26. https://doi.org/10.1214/aos/1176344552.",
    "wilson1927": "E.B. Wilson, Probable inference, the law of succession, and statistical inference, J. Am. Stat. Assoc. 22 (1927) 209–212. https://doi.org/10.1080/01621459.1927.10502953.",
    "tfblog": f"TensorFlow Blog, Easier object detection on mobile with TensorFlow Lite, 2021. https://blog.tensorflow.org/2021/06/easier-object-detection-on-mobile-with-tf-lite.html ({ACCESSED}).",
    "mp_objdet": f"Google, Object detection task guide, MediaPipe Solutions. https://developers.google.com/edge/mediapipe/solutions/vision/object_detector ({ACCESSED}).",
    "deepface": f"S.I. Serengil, DeepFace benchmarks, GitHub repository. https://github.com/serengil/deepface/tree/master/benchmarks ({ACCESSED}).",
}


class Cites:
    def __init__(self):
        self.order = []

    def label(self, keys):
        numbers = []
        for key in keys.split(","):
            key = key.strip()
            if key not in REFS:
                raise KeyError(key)
            if key not in self.order:
                self.order.append(key)
            numbers.append(self.order.index(key) + 1)
        numbers = sorted(set(numbers))
        parts, start, last = [], numbers[0], numbers[0]
        for n in numbers[1:] + [None]:
            if n is not None and n == last + 1:
                last = n
                continue
            parts.append(str(start) if start == last else f"{start}–{last}" if last > start + 1 else f"{start},{last}")
            start = last = n
        return "[" + ",".join(parts) + "]"


CITES = Cites()
COUNT = {"words": 0, "abstract": 0}


def rich(paragraph, text, size=11, bold=None, italic=None, count=True, font=FONT):
    """[[keys]] become bracketed reference numbers, <i>/<b> switch style, {{...}} is a highlighted blank."""
    state = {"i": bool(italic), "b": bool(bold)}
    for part in re.split(r"(\[\[.*?\]\]|</?[ib]>|\{\{.*?\}\})", text):
        if not part:
            continue
        if part in ("<i>", "</i>", "<b>", "</b>"):
            state[part.strip("</>")] = not part.startswith("</")
            continue
        label = CITES.label(part[2:-2]) if part.startswith("[[") else part
        run = paragraph.add_run(label)
        if part.startswith("{{"):
            run.font.highlight_color = 7
        if count and not part.startswith("[["):
            COUNT["words"] += len(part.split())
        run.bold = state["b"] or None
        run.italic = state["i"] or None
        run.font.name = font
        run.font.size = Pt(size)
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "Malgun Gothic")
    return paragraph


class Paper:
    def __init__(self, template):
        self.doc = Document(str(template))
        self.metadata = copy.deepcopy(self.doc.tables[0]._tbl)        # the template's own table, kept as it is
        body = self.doc.element.body
        for child in list(body):
            if child.tag != qn("w:sectPr"):
                body.remove(child)

    def heading(self, text, size=11):
        paragraph = self.doc.add_paragraph(style="Heading 1")
        paragraph.paragraph_format.space_before, paragraph.paragraph_format.space_after = Pt(12), Pt(4)
        paragraph.paragraph_format.keep_with_next = True
        rich(paragraph, text, size=size, bold=True, count=False)
        return paragraph

    def para(self, text, align=WD_ALIGN_PARAGRAPH.JUSTIFY, **kwargs):
        paragraph = self.doc.add_paragraph(style="Body")
        paragraph.alignment = align
        paragraph.paragraph_format.space_after = Pt(6)
        return rich(paragraph, text, **kwargs)

    def bullets(self, items, **kwargs):
        for item in items:
            paragraph = self.doc.add_paragraph(style="List Paragraph")
            paragraph.paragraph_format.space_after = Pt(3)
            paragraph.paragraph_format.left_indent, paragraph.paragraph_format.first_line_indent = Cm(0.9), Cm(-0.5)
            rich(paragraph, "•\u00a0 " + item, **kwargs)

    def caption(self, label, text):
        paragraph = self.doc.add_paragraph(style="Body")
        paragraph.paragraph_format.space_after = Pt(8)
        rich(paragraph, f"<b>{label}.</b> {text}", size=9.5)

    def figure(self, path, key, text, width=15.0):
        paragraph = self.doc.add_paragraph()
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
        paragraph.paragraph_format.keep_with_next = True
        paragraph.paragraph_format.space_after = Pt(2)
        paragraph.add_run().add_picture(str(path), width=Cm(width))
        self.caption(F(key), text)

    def table(self, header, rows, widths, size=8.5, first_bold=False):
        grid = self.doc.add_table(rows=1, cols=len(header))
        grid.autofit = False
        borders = OxmlElement("w:tblBorders")                 # the template has no "Table Grid" style
        for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
            line = OxmlElement(f"w:{edge}")
            for name, value in (("w:val", "single"), ("w:sz", "4"), ("w:space", "0"), ("w:color", "808080")):
                line.set(qn(name), value)
            borders.append(line)
        grid._tbl.tblPr.append(borders)
        repeat = OxmlElement("w:tblHeader")
        repeat.set(qn("w:val"), "true")
        grid.rows[0]._tr.get_or_add_trPr().append(repeat)
        for row_index, values in enumerate([header] + rows):
            cells = grid.rows[0].cells if row_index == 0 else grid.add_row().cells
            for column, (cell, value) in enumerate(zip(cells, values)):
                cell.width = Cm(widths[column])
                paragraph = cell.paragraphs[0]
                paragraph.paragraph_format.space_after = Pt(0)
                paragraph.paragraph_format.line_spacing = 1.0
                rich(paragraph, str(value), size=size, bold=row_index == 0 or (first_bold and column == 0), count=False)
                if row_index == 0:
                    base.shade(cell, "EDEDEA")
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)

    def metadata_table(self, values):
        self.doc.element.body.insert(len(self.doc.element.body) - 1, self.metadata)
        table = self.doc.tables[-1]
        for row, value in zip(table.rows[1:], values):
            cell = row.cells[2]
            for extra in cell.paragraphs[1:]:
                extra._p.getparent().remove(extra._p)
            paragraph = cell.paragraphs[0]
            for child in list(paragraph._p):
                if child.tag != qn("w:pPr"):
                    paragraph._p.remove(child)
            rich(paragraph, value, size=10, count=False, font="Calibri")
        self.doc.add_paragraph().paragraph_format.space_after = Pt(2)


def build(results, paths, template, target):
    det = {(e["model"], e["delegate"]): e for e in results["detection"]}
    faces, extras = results["faces"], results["extras"]
    ver, ident = faces["verification"], faces["identification"]["by_saved_people"]
    L2, L2C = det[("efficientdet-lite2.tflite", "GPU")], det[("efficientdet-lite2.tflite", "CPU")]
    L0, I8, SSD = det[("efficientdet-lite0.tflite", "GPU")], det[("efficientdet-lite0-int8.tflite", "CPU")], det[("ssd-mobilenet-v2.tflite", "GPU")]
    rule, tune, sweep = extras["face_rule_ablation"], extras["face_threshold"], extras["detection_threshold_sweep"]
    precisions = [e["precision"] for e in det.values()]
    v = ms.versions()
    p = Paper(template)

    # ---- front matter ----
    title = p.doc.add_paragraph(style="Heading 1")
    rich(title, "Nungil: an on-device scene assistant for blind and low-vision users on Android phones", size=14, bold=True, count=False)
    p.para("{{Author names}}", align=WD_ALIGN_PARAGRAPH.LEFT, count=False)
    p.para("{{Affiliations with postal addresses; e-mail of the corresponding author}}", align=WD_ALIGN_PARAGRAPH.LEFT, count=False, size=10)

    p.heading("Abstract")
    COUNT["words"] = 0
    p.para("At least 2.2 billion people have a vision impairment and 43.3 million are blind. Most smartphone assistants need a cloud service or extra hardware, and their accuracy is seldom reported at the settings users experience. "
           "This study introduces Nungil, an Android application that runs object detection, a summary of a full turn, recognition of saved people and objects and hazard warnings on the phone alone. "
           f"On a mid-range phone its detector reaches {pct(L2['precision'])} precision on COCO at {L2['ms_mean']:.0f} ms per image; its matching rule names {pct(ident['10']['named_right'])} of saved people correctly and cuts false naming of strangers from {pct(rule[0]['stranger_named'])} to {pct(rule[3]['stranger_named'])}. "
           "A bundled benchmark reproduces the results.")
    COUNT["abstract"] = COUNT["words"]
    p.heading("Keywords")
    p.para("Assistive technology; Visual impairment; On-device inference; Object detection; Face recognition; Android", align=WD_ALIGN_PARAGRAPH.LEFT)

    p.heading("Metadata")
    p.caption(T("metadata"), "Code metadata.")
    p.metadata_table([
        "{{v1.0}}",
        "https://github.com/androidIkrom/hackathon-ml-scanner",
        "{{Apache License 2.0}}",
        "git",
        "Kotlin, Android SDK, CameraX, MediaPipe Tasks, ML Kit, TensorFlow Lite, ARCore, Room; Python (benchmark analysis)",
        f"Android {'7.0' if v['min'] == '24' else 'API ' + v['min']} or later with a gyroscope and a compass; ARCore depth for walk mode. Build: JDK 17 or later, Android SDK {v['target']}, Gradle with Android Gradle Plugin {v['agp']}; models are downloaded at build time",
        "https://github.com/androidIkrom/hackathon-ml-scanner (README.md, docs/build-guide.md)",
        "{{support e-mail}}",
    ])

    # ---- 1 Motivation and significance ----
    p.heading("1. Motivation and significance")
    p.para("Loss of sight narrows everyday life in ways that sighted people seldom notice: knowing what stands in a room, finding a bag, recognising who is approaching, reading a sign or noticing a step. "
           "At least 2.2 billion people worldwide have a near or distance vision impairment [[who2026]]; in 2020 an estimated 43.3 million people were blind and 295 million had moderate or severe vision impairment, and both numbers rose over the preceding three decades [[bourne2021]]. "
           "The smartphone has become the most accessible platform for assistive technology, since it combines a camera, motion sensors, speech input and output and enough computing power to run neural networks [[khan2021,kuriakose2022,budrionis2022]]. "
           "Deep-learning assistive services exist for other sensory impairments as well [[manship]], and several applications for blind users are in daily use, among them Seeing AI [[seeingai]], Lookout [[lookout]], Be My Eyes [[bme]] and Envision [[envision]].")
    p.para("Despite this progress, three problems remain. First, many systems send images to a server or a cloud model, or need dedicated hardware such as smart glasses, depth cameras or single-board computers; this raises cost, requires connectivity and moves images of the user's surroundings off the device. "
           "Second, published systems are evaluated on their own data with their own metrics, and they report the accuracy of a model rather than of what the application finally announces after its thresholds and filters, so their results cannot be compared. "
           "Third, recognising familiar people is an open-set problem: a stranger must never be greeted by a saved name, yet this error is rarely measured. "
           "Consequently, this study presents Nungil (눈길), an open-source Android application that performs all recognition on the phone, together with a benchmark that measures its deployed pipeline on public datasets. The main contributions are:")
    p.bullets([
        "An on-device assistant that combines object detection with compass direction and colour, a spoken summary of a full 360° turn, find-object guidance, recognition of saved people and personal objects, text and code reading and depth-based walking hazards, controlled by voice in English and Korean.",
        "A de-duplication rule that turns hundreds of frames into one correct count, and an open-set face-matching rule whose parts are quantified by an ablation on Labeled Faces in the Wild.",
        f"A reproducible benchmark that runs the application's own detector and face pipeline on the phone over COCO and LFW and reports precision, recall, latency and identification rates with 95% confidence intervals.",
    ])
    p.para("The user holds the phone upright and either turns on the spot or walks. After the wake word \"Eye\" (Korean \"눈길\") a spoken command starts a function; the phone answers by speech, beeps and vibration and shows every utterance as a caption for sighted helpers. "
           "No account, network connection or external device is needed; only the optional walking route is fetched online. Section 2 describes the architecture and the functionalities, Section 3 illustrates a session and reports the benchmark, Section 4 discusses the impact and Section 5 concludes.")
    p.para(f"{T('research')} summarises twelve representative systems published between 2017 and 2026; it is a selection, not a systematic review. Server- and cloud-based systems recognise objects on a server [[lin2017]] or describe scenes with vision-language models [[chen2024,walkvlm,lin2026]]; "
           "wearable and embedded systems add RGB-D glasses [[bai2019]], single-board computers [[joshi2020,zhang2024,mekonnen2025]], FPGA glasses [[gao2025]] or smart glasses paired with a phone [[alhammad2026]]; smartphone-only systems are rare [[see2022,kuriakose2023]]. "
           "None of the twelve reports results on a public benchmark such as COCO or LFW, none reports colour naming, a summary of a full turn or user-taught personal objects, and face enrolment appears in two [[mekonnen2025,alhammad2026]]. "
           "A concurrent preprint, VisionAId, an offline-first Android assistant with a few-shot pipeline for personal objects, is the closest in spirit; it reports a depth latency of about 491 ms and a banknote-detector mAP@50 of 0.986 on a Samsung Galaxy S21 Ultra, but no public-benchmark results and no user study [[visionaid]]. The commercial applications publish no accuracy figures at all [[seeingai,lookout,bme,envision]].")
    p.caption(T("research"), "Twelve published assistive systems and Nungil. VI = visual impairment, OD = object detection, Dir = direction, Dist = distance, Find = find-object guidance, Face = face recognition, OCR = text reading, Obs = obstacle warning, Nav = navigation, Scene = scene description. Values are those the papers report; \"n.r.\" = not reported, \"n.a.\" = text not accessible.")
    research = [
        ["Lin et al. 2017 [[lin2017]]", "Phone + PC server (mixed)", "OD, Dir, Dist, Obs", "Own 7-class images: recognition rate 55–60%", "n.r.", "4"],
        ["Bai et al. 2019 [[bai2019]]", "RGB-D glasses + phone (on-device)", "Obs, OD, Nav", "n.r.", "114 ms (phone)", "20"],
        ["Joshi et al. 2020 [[joshi2020]]", "Single-board computer (on-device)", "OD, Obs", "Own data: accuracy 95.19%", "0.1–0.3 s", "36"],
        ["See et al. 2022 [[see2022]] (abstract)", "Depth-camera phone (n.a.)", "Obs, OD, Find, Voice", "Objects located > 80% of the time", "n.a.", "n.a."],
        ["DeepNAVI 2023 [[kuriakose2023]]", "Phone (on-device)", "OD, Dir, Dist, Scene", "Own 20-class data: accuracy 87.8%", "n.r.", "1"],
        ["Zhang et al. 2024 [[zhang2024]]", "Raspberry Pi + depth camera (on-device)", "Find, Dist, Voice", "Own 3-class data: mAP 93.9%", "4.3 ms (hardware n.r.)", "n.r."],
        ["Chen et al. 2024 [[chen2024]]", "Wearable + cloud GPU (cloud)", "Scene", "POPE accuracy 0.86–0.89", "0.4–4.4 s", "12"],
        ["Gao et al. 2025 [[gao2025]]", "FPGA glasses + phone (mixed)", "Obs", "Collision avoidance 100% (720 trials)", "< 320 ms", "20"],
        ["Mekonnen et al. 2025 [[mekonnen2025]]", "Raspberry Pi 5 (on-device)", "OD, OCR, Face, Voice", "Own tests: OD precision 85.2%, face 81.3%", "0.8 s per frame", "none"],
        ["WalkVLM 2025 [[walkvlm]]", "Cloud VLM (cloud)", "Walking reminders, Scene", "Own data: text-similarity scores", "n.r.", "3 + 34 surveys"],
        ["Munir 2026 [[alhammad2026]]", "Smart glasses + phone (mixed)", "Face, OCR, Scene, OD, Voice", "SCface: recognition 96.69%", "44 ms face (phone)", "none"],
        ["Lin et al. 2026 [[lin2026]]", "Head-mounted camera + laptop (mixed)", "Nav, OD, Obs, Scene, Voice", "RePOPE accuracy 87.9–90.9%", "3.3 s", "none"],
        ["Nungil (this work)", "Phone alone (on-device)", "OD, Dir, colour, 360° summary, Find, Face, objects, OCR, Obs, Nav, Voice",
         f"COCO: AP {100 * L2['ap']:.1f}, precision {pct(L2['precision'])}; LFW: {100 * ver['ten_fold_accuracy']:.2f}%", f"{L2['ms_mean']:.0f} ms (phone)", "none yet"],
    ]
    p.table(["System", "Platform (where inference runs)", "Functions", "Evaluation data and reported accuracy", "Reported latency", "Participants with VI"], research, [3.0, 3.2, 3.0, 3.6, 1.8, 2.0], size=8, first_bold=True)

    # ---- 2 Software description ----
    p.heading("2. Software description")
    p.heading("2.1 Software architecture")
    p.para(f"{F('architecture')} shows the three layers of the application. Sensor data from the camera (CameraX, or ARCore in walk mode), the rotation-vector sensor, the microphone and GPS pass through neural models that run on the device: the MediaPipe object detector [[mediapipe]] with EfficientDet-Lite [[efficientdet]] or SSD MobileNet V2 [[ssd,mobilenetv2]], an EfficientNet-Lite0 classifier that names the middle of the frame when nothing is detected [[efficientnet]], ML Kit face, text and barcode recognition [[mlkit]], FaceNet-512 [[facenet,facenet_android]] and a MobileNetV3-small image embedder [[mobilenetv3]]. "
           "Their outputs enter a rule layer written in pure Kotlin without Android dependencies, which decides what is worth saying; it is unit-tested on a desktop machine. Speech, beeps, vibration and captions form the output.")
    p.figure(paths["architecture"], "architecture", "Architecture of Nungil. Sensor data are processed by on-device models and by a rule layer in pure Kotlin; nothing is sent to a server.")
    p.para(f"Nungil is written in Kotlin {v['kotlin']} for Android {'7.0' if v['min'] == '24' else 'API ' + v['min']} and later with CameraX {v['camerax']}, MediaPipe Tasks Vision {v['mediapipe']}, ML Kit, TensorFlow Lite {v['tflite']}, ARCore {v['arcore']} [[arcoredepth]] and Room {v['room']}. "
           "It consists of 165 Kotlin source files (16,046 lines); the rule layer of 81 files (6,242 lines) is covered by 733 unit tests. The eight model files (4.1 to 24.4 MB each, about 106 MB in total) are downloaded at build time. "
           "Face embeddings and saved objects are stored in a local database; the only network request is an optional walking route, which falls back to an offline direction beacon. The repository also holds the benchmark: an instrumented test for the phone and Python scripts that download the datasets and compute every table and figure of this article.")

    p.heading("2.2 Software functionalities")
    p.para("<b>Detection and direction.</b> Each frame yields up to ten boxes from the 80 COCO classes. A box is announced when its score reaches the user's setting (0.7 by default, adjustable down to 0.3; people are accepted 0.2 lower), and a box cut by one side edge of the frame is dropped. "
           "The direction of an object combines the heading <i>h</i> of the camera axis from the rotation-vector sensor with the horizontal position <i>x</i> of the box centre inside the field of view, θ = (<i>h</i> + (<i>x</i> − 0.5)·FOV) mod 360°, where FOV = 2·arctan(<i>w</i>/2<i>f</i>) follows from the sensor width <i>w</i> and focal length <i>f</i> of the camera. The angle is spoken as front, right, behind or left.")
    p.para("<b>De-duplicated summary.</b> A full turn ends when 36 heading bins of 10° are covered or after 60 s. Detections of the same class within 20° are merged into one cluster whose count is the largest number seen in a single frame, never a sum over frames, spoken only after three frames, with the majority colour of its detections. "
           "The result is one sentence: \"Around you: 3 blue chairs in front; a black laptop on your right.\"")
    p.para("<b>Colour.</b> 24 × 24 samples from the central half of a box vote, after a gray-world white balance, for one of eleven basic colour names by hue, saturation and value; a chromatic name wins when chromatic samples reach 30% of the named ones. No colour is given in dark frames or for people.")
    p.para("<b>Saved people.</b> A face is used when it is at least 64 pixels in size and turned no more than 35° sideways or 25° up or down. The crop is levelled by the eye line and embedded twice with FaceNet-512, plain and mirrored, and the embeddings are averaged. "
           "Matching is open-set: every saved person is scored by the mean cosine similarity of the three samples closest to the probe, the best score must reach 0.5 and must exceed the runner-up by 0.08; otherwise the face stays unnamed. A person is enrolled in one guided sweep of the head.")
    p.para("<b>Saved objects and find guidance.</b> Personal objects are recognised by appearance with MobileNetV3-small embeddings at a cosine threshold of 0.75. When the user asks to find something, by class, saved name or saved object, the phone beeps faster as the target moves to the centre of the frame, from one beep per second at the edge to one every 150 ms, vibrates when it is centred and reports a lost target after 3 s.")
    p.para("<b>Reader and walk mode.</b> The reader speaks printed Latin text, QR codes and barcodes. In walk mode the ARCore depth map is inspected between 30% and 62% of the image height in three zones; a zone is blocked when at least 40% of its readings within 0.3–8 m are closer than 3 m, and unknown when fewer than 20% of them are valid. A floor profile ahead yields steps, stairs and drop-offs; the application never says that the way is safe.")
    p.para("<b>Voice and accessibility.</b> The application listens continuously for the wake word; every function can be started by voice in English and Korean, application sound stops while the user speaks, and the screens offer high-contrast themes, large touch targets and screen-reader labels.")

    # ---- 3 Illustrative examples ----
    p.heading("3. Illustrative examples")
    p.para(f"<b>A session.</b> The user says \"Eye, look around\" and turns once on the spot; the coverage ring on the screen fills and the phone says \"Around you: 3 blue chairs in front; a black laptop on your right.\" \"Find my bag\" starts the beeps, which speed up as the camera points at the bag and end with a vibration. "
           f"\"Who is this\" names a saved person or stays silent for a stranger, and \"walk mode\" switches to depth-based warnings such as \"Wall ahead, 3 steps\". {F('screens')} shows the corresponding screens.")
    screens = p.doc.add_paragraph()
    screens.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rich(screens, "{{Screenshots: home screen, full scan with the coverage ring, find mode, a saved person, walk mode}}", count=False)
    p.caption(F("screens"), "Main screens of Nungil. {{to be added}}")
    p.para(f"<b>Benchmarking the deployed pipeline.</b> The second example is the benchmark shipped with the code. A script downloads a fixed random sample of {L2['images']:,} images of COCO val2017 [[coco]] ({L2['tp'] + L2['fn']:,} annotated objects) and all {faces['images']:,} images of LFW [[lfw]]; the images are pushed to the phone, and an instrumented test runs the application's own detector factory and face path over them, so that every configuration sees the same input. "
           "A second script turns the raw detections, embeddings and times into the numbers below; the test runs behind a blank screen, because the phone's power manager froze it in the background.")
    p.para(f"The phone was an Infinix X6880 (MediaTek Helio G99, Android 15) on a charger. Detection is reported as COCO AP at the deployed settings (detector score 0.3, at most 10 results) and as precision and recall at the default announcement thresholds, with greedy matching at an intersection over union of 0.5. Faces are reported on the standard 6,000 verification pairs and in an open-set protocol that mirrors the application: "
           f"<i>G</i> saved people drawn from the {faces['identification']['people_eligible']} identities with at least eight usable images, five images enrolled each, five further images as genuine probes and as many images of other people as stranger probes, 300 random trials per setting. Intervals are 95%, by bootstrap over images or trials [[efron1979]] and by the Wilson method for verification [[wilson1927]].")
    p.caption(T("detection"), f"Object detection on the phone over {L2['images']:,} COCO val2017 images. Precision and recall are at the application's thresholds with 95% confidence intervals; latency is the mean per image without the first 20 frames.")
    rows = []
    for key in ORDER:
        e = det.get(key)
        if e:
            rows.append([NAMES[e["model"]], e["delegate"], f"{100 * e['ap']:.1f}", pct(e["precision"], e["precision_ci"]), pct(e["recall"], e["recall_ci"]), pct(e["recall_by_size"]["large"]), f"{e['ms_mean']:.0f}", f"{1000 / e['ms_mean']:.1f}"])
    p.table(["Model", "Delegate", "AP", "Precision", "Recall", "Large-object recall", "Latency, ms", "FPS"], rows, [3.3, 1.9, 1.0, 2.5, 2.5, 1.8, 1.5, 1.1])
    p.para(f"{T('detection')} and {F('tradeoff')} show that precision lies between {pct(min(precisions))} and {pct(max(precisions))} for every configuration: the application rarely names something that is not there. Recall is low by design, {pct(L2['recall'])} over all annotated objects and {pct(L2['recall_by_size']['large'])} over large ones for the default model, because COCO is full of small, distant objects that an assistant need not announce and at most ten objects are reported per frame. "
           f"The default model needs {L2['ms_mean']:.0f} ms per image ({1000 / L2['ms_mean']:.1f} frames per second), {L2C['ms_mean'] / L2['ms_mean']:.1f} times less than on the CPU at the same accuracy; the int8 model of walk mode needs {I8['ms_mean']:.0f} ms. "
           f"The announcement threshold is a user setting, and {F('threshold')} shows its trade-off: at 0.3 recall rises to {pct(sweep[0]['recall'])} while precision falls to {pct(sweep[0]['precision'])}. The measured AP lies {33.97 - 100 * L2['ap']:.1f} and {25.69 - 100 * L0['ap']:.1f} points below the figures published for the whole validation set without deployed limits [[tfblog]], and the phone is {SSD['ms_mean'] / 24.01:.1f} to {L2['ms_mean'] / 41.15:.1f} times slower on the GPU than the Pixel 6 of the published latencies [[mp_objdet]].")
    p.figure(paths["tradeoff"], "tradeoff", "Accuracy against speed for the seven detector configurations (SSD = SSD MobileNet V2). The delegate changes the speed but not the accuracy.")
    p.figure(paths["threshold"], "threshold", "Precision and recall of the default model for the announcement thresholds the application allows.")
    p.caption(T("faces"), f"Face pipeline on the phone over LFW ({faces['images']:,} images). Identification rows use 10 saved people with 5 enrolled images each; 95% confidence intervals in brackets.")
    p.table(["Measure", "Value"], [
        ["Face found / passes the quality gate", f"{pct(faces['face_found'])} / {pct(faces['embedded'])}"],
        ["Verification accuracy, standard 10-fold protocol", f"{100 * ver['ten_fold_accuracy']:.2f}% ± {100 * ver['ten_fold_sd']:.2f}"],
        ["True accept / false accept rate at the deployed threshold 0.5", f"{pct(ver['true_accept'], ver['true_accept_ci'])} / {pct(ver['false_accept'], ver['false_accept_ci'])}"],
        ["Time per face, median: detection + embedding", f"{faces['find_ms_median']:.0f} + {faces['embed_ms_median']:.0f} ms"],
    ] + [[f"{r['rule']}: saved person named correctly / stranger given a saved name", f"{pct(r['named_right'], r['named_right_ci'])} / {pct(r['stranger_named'], r['stranger_named_ci'])}"] for r in rule]
      + [[f"Deployed rule, threshold {r['threshold']}: named correctly / stranger named", f"{pct(r['named_right'])} / {pct(r['stranger_named'])}"] for r in tune if r["threshold"] in (0.6, 0.7)],
      [9.6, 6.4], size=8.5)
    p.para(f"{T('faces')} gives the face results. A face was found in every image and {pct(faces['embedded'])} passed the quality gate; verification accuracy under the standard protocol is {100 * ver['ten_fold_accuracy']:.2f}%, against 98.4% reported for FaceNet-512 by an independent benchmark [[deepface]], whose detector and crop differ. "
           f"The ablation in {F('ablation')} isolates the matching rule: the single-sample rule the project started from, cosine 0.3, names a saved person correctly in {pct(rule[0]['named_right'])} of cases but also gives a saved name to {pct(rule[0]['stranger_named'])} of strangers; the threshold of 0.5 lowers that share to {pct(rule[1]['stranger_named'])}, the mean of the three best samples to {pct(rule[2]['stranger_named'])} and the margin to {pct(rule[3]['stranger_named'])}, "
           f"at a cost of {100 * (rule[0]['named_right'] - rule[3]['named_right']):.1f} percentage points in correct naming. {F('gallery')} shows that every additional saved person is one more chance of a false match: strangers receive a saved name in {pct(ident['1']['stranger_named'])} of cases with one saved person and {pct(ident['20']['stranger_named'])} with twenty, while saved people are practically never confused with each other. "
           f"A threshold of 0.6 would reduce the share with ten saved people to {pct(tune[2]['stranger_named'])} while still naming {pct(tune[2]['named_right'])} of saved people correctly.")
    p.figure(paths["ablation"], "ablation", "Effect of each part of the face-matching rule with ten saved people; whiskers are 95% confidence intervals. The left axis does not start at zero.")
    p.figure(paths["gallery"], "gallery", "Open-set identification against the number of saved people; whiskers are 95% confidence intervals. The panels have different vertical scales, and the left one does not start at zero.")

    # ---- 4 Impact ----
    p.heading("4. Impact")
    p.para("Nungil changes the question that can be asked of an assistive application from \"how accurate is its model\" to \"how often is what it says right, at the settings its users experience\". The benchmark separates the two: the same models that publish 25–34 COCO mAP announce, after the application's thresholds, "
           f"{pct(L2['precision'])} correct objects while finding {pct(L2['recall'])} of them, and a verification false-accept rate of {pct(ver['false_accept'])} per pair becomes {pct(ident['10']['stranger_named'])} of strangers named with ten saved people. "
           "These are the figures a blind user lives with, and to our knowledge no assistive system has reported them before. The open-set effect in particular, that false naming grows with every person saved, is invisible in a verification accuracy and raises new questions: how the threshold should depend on the number of saved people, how many enrolment samples are worth their cost, and how users weigh a missed object against a wrong one.")
    p.para("For existing research the software offers comparability. The twelve systems in Table 2 cannot be ranked because each was measured on its own data; because the benchmark runs the deployed pipeline on public datasets with confidence intervals and is part of the repository, another system can be measured in the same way on the same phone, and every number reported here is reproducible from the raw output. "
           "The approach, running an application's own classes over stored images on the device, suits any on-device vision application.")
    p.para("For practice, Nungil removes two conditions that most assistants impose: a network connection and extra hardware. A single mid-range phone describes a room in one sentence, finds a named object, recognises a saved person and warns about a wall, in English and in Korean, and the images never leave the device, which matters for a camera that is pointed at other people all day. "
           "The application is new: it has been used by its developers on one phone, has not yet been evaluated with blind or low-vision users and has no downloads or commercial use to report. Its limitations are stated plainly: recall for small objects is low, Korean text is not read, and colour naming, find-object guidance and walk mode are implemented but not yet benchmarked.")

    # ---- 5 Conclusions ----
    p.heading("5. Conclusions")
    p.para(f"Nungil is an open-source Android application that performs all recognition for a blind or low-vision user on the phone and is distributed together with a benchmark that measures it at its deployed settings on public datasets. On a mid-range phone its default detector is right in {pct(L2['precision'])} of its announcements and finds {pct(L2['recall_by_size']['large'])} of large objects at {L2['ms_mean']:.0f} ms per image, "
           f"saved people are named correctly in {pct(ident['10']['named_right'])} of cases with ten people saved, and a three-part matching rule reduces false naming of strangers from {pct(rule[0]['stranger_named'])} to {pct(rule[3]['stranger_named'])}. "
           "Future work will evaluate the functions that were not benchmarked, raise recall for small objects, add Korean text recognition and, above all, study the application with the people it is built for.")

    p.heading("Acknowledgements")
    p.para("{{Optional: colleagues who helped but do not qualify as authors; funding, or a statement that no funding was received.}}", count=False)
    p.heading("Declaration of competing interest")
    p.para("{{The authors declare that they have no known competing financial interests or personal relationships that could have appeared to influence the work reported in this paper.}}", count=False)
    p.heading("Declaration of generative AI and AI-assisted technologies in the manuscript preparation process")
    p.para("{{During the preparation of this work the authors used Claude (Anthropic) to write the benchmark and analysis scripts, to prepare the figures and to draft the text. After using this tool, the authors reviewed and edited the content as needed and take full responsibility for the content of the publication.}}", count=False)

    p.heading("References")
    for number, key in enumerate(CITES.order, 1):
        paragraph = p.doc.add_paragraph(style="Body")
        paragraph.paragraph_format.space_after = Pt(3)
        paragraph.paragraph_format.left_indent, paragraph.paragraph_format.first_line_indent = Cm(0.9), Cm(-0.9)
        rich(paragraph, f"[{number}] {REFS[key]}", size=9.5, count=False)
    unused = [key for key in REFS if key not in CITES.order]
    assert not unused, unused
    assert COUNT["abstract"] <= ABSTRACT_WORDS, COUNT["abstract"]
    assert COUNT["words"] <= WORD_LIMIT, COUNT["words"]
    p.doc.save(target)
    return COUNT


def body_only(target, out):
    """A copy without tables, pictures and front/back matter, to count the pages of the main text (Sections 1-5)."""
    doc = Document(str(target))
    body = doc.element.body
    keep, started = [], False
    for child in list(body):
        text = "".join(t.text or "" for t in child.iter(qn("w:t")))
        if child.tag == qn("w:p") and text.startswith("1. Motivation and significance"):
            started = True
        if child.tag == qn("w:p") and text.startswith("Acknowledgements"):
            started = False
        drop = not started or child.tag == qn("w:tbl") or child.find(".//" + qn("w:drawing")) is not None
        if drop and child.tag != qn("w:sectPr"):
            body.remove(child)
    doc.save(str(out))


if __name__ == "__main__":
    data = Path(sys.argv[1] if len(sys.argv) > 1 else "D:/nungil-bench")
    template = Path(sys.argv[2]) if len(sys.argv) > 2 else data / "reference" / "template" / "softwarex-osp-template.docx"
    target = data / (sys.argv[3] if len(sys.argv) > 3 else "Nungil-softwarex.docx")
    results = json.loads((data / "results.json").read_text())
    paths = ms.figures(results, data / "figures")
    counts = build(results, paths, template, target)
    body_only(target, target.with_name(target.stem + "-bodyonly.docx"))
    print("wrote", target, "| counted words:", counts["words"], "| abstract:", counts["abstract"], "| references:", len(CITES.order))
