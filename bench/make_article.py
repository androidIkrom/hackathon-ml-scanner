"""Builds the article base (figures + Word file) from the measured results.

    python bench/make_article.py [data-dir] [file-name]    (default D:/nungil-bench; run analyze.py first)

Writes <data-dir>/figures/*.png and <data-dir>/Nungil-article-base.docx. Every number in the tables and
figures is read from <data-dir>/results.json; nothing is typed in by hand except the cited published values.
"""
import json
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from docx import Document
from docx.enum.section import WD_ORIENT, WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

BLUE, ORANGE = "#2a78d6", "#eb6834"
SIZES = {"small": "#9ec5f4", "medium": "#3987e5", "large": "#184f95"}
INK, SECONDARY, MUTED, GRID, AXIS = "#0b0b0b", "#52514e", "#898781", "#e1e0d9", "#c3c2b7"
NAMES = {
    "efficientdet-lite2.tflite": "EfficientDet-Lite2",
    "efficientdet-lite0.tflite": "EfficientDet-Lite0",
    "efficientdet-lite0-int8.tflite": "EfficientDet-Lite0 int8",
    "ssd-mobilenet-v2.tflite": "SSD MobileNet V2",
}
ORDER = [(m, d) for m in NAMES for d in ("GPU", "CPU")]
# Published values, each with its reference number in REFERENCES below.
PUBLISHED = {
    "efficientdet-lite2.tflite": ("33.97", "41.15 / 197.98"),
    "efficientdet-lite0.tflite": ("25.69", "27.83 / 61.30"),
    "efficientdet-lite0-int8.tflite": ("not stated", "n/a / 29.31"),
    "ssd-mobilenet-v2.tflite": ("20.2 (320 px variant)", "24.01 / 36.30"),
}


def pct(value, interval=None, digits=1):
    text = f"{100 * value:.{digits}f}%"
    return text + (f" [{100 * interval[0]:.{digits}f}, {100 * interval[1]:.{digits}f}]" if interval else "")


def style_axes(ax, grid_axis):
    for side in ("top", "right"):
        ax.spines[side].set_visible(False)
    for side in ("left", "bottom"):
        ax.spines[side].set_color(AXIS)
    ax.tick_params(colors=MUTED, labelcolor=SECONDARY, labelsize=9, length=0)
    ax.grid(axis=grid_axis, color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)


def figures(results, folder: Path) -> dict:
    folder.mkdir(exist_ok=True)
    det = {(e["model"], e["delegate"]): e for e in results["detection"]}
    plt.rcParams.update({"font.family": "DejaVu Sans", "text.color": INK, "axes.labelcolor": SECONDARY, "axes.titlesize": 11})
    paths = {}

    # Figure 1: latency per model and delegate.
    fig, ax = plt.subplots(figsize=(6.3, 3.2))
    models = list(NAMES)
    for row, model in enumerate(models):
        for offset, delegate, colour in ((-0.19, "GPU", ORANGE), (0.19, "CPU", BLUE)):
            entry = det.get((model, delegate))
            if entry:
                y = row + offset
                ax.barh(y, entry["ms_mean"], height=0.34, color=colour, label=delegate if row == 0 else None)
                ax.text(entry["ms_mean"] + 6, y, f"{entry['ms_mean']:.0f} ms", va="center", fontsize=9, color=INK)
    ax.set_yticks(range(len(models)), [NAMES[m] for m in models])
    ax.invert_yaxis()
    ax.set_xlabel("Mean inference time per image (ms)")
    ax.set_xlim(0, max(e["ms_mean"] for e in det.values()) * 1.15)
    style_axes(ax, "x")
    ax.legend(frameon=False, fontsize=9, loc="lower right")
    paths["latency"] = save(fig, folder / "fig1-latency.png")

    # Figure 2: accuracy against speed.
    fig, ax = plt.subplots(figsize=(6.3, 3.6))
    short = {"efficientdet-lite2.tflite": "Lite2", "efficientdet-lite0.tflite": "Lite0", "efficientdet-lite0-int8.tflite": "Lite0 int8", "ssd-mobilenet-v2.tflite": "SSD"}
    nudges = {("efficientdet-lite0.tflite", "GPU"): (-6, 7), ("efficientdet-lite0-int8.tflite", "CPU"): (7, -14),
              ("ssd-mobilenet-v2.tflite", "GPU"): (6, -15), ("ssd-mobilenet-v2.tflite", "CPU"): (9, -2)}
    for delegate, colour in (("GPU", ORANGE), ("CPU", BLUE)):
        points = [e for (m, d), e in det.items() if d == delegate]
        ax.scatter([e["ms_mean"] for e in points], [100 * e["ap"] for e in points], s=46, color=colour, label=delegate,
                   edgecolors="white", linewidths=1.2, zorder=3)
    for (model, delegate), entry in det.items():
        dx, dy = nudges.get((model, delegate), (8, 5))
        ax.annotate(f"{short[model]}, {delegate}", (entry["ms_mean"], 100 * entry["ap"]),
                    xytext=(dx, dy), textcoords="offset points", fontsize=8.5, color=SECONDARY, ha="right" if dx < 0 else "left")
    ax.set_xlabel("Mean inference time per image (ms)")
    ax.set_ylabel("COCO AP@[.5:.95] (%)", labelpad=8)
    ax.set_xlim(0, 500)
    ax.set_ylim(16, 33)
    style_axes(ax, "both")
    ax.legend(frameon=False, fontsize=9, loc="lower right")
    paths["tradeoff"] = save(fig, folder / "fig2-accuracy-speed.png")

    # Figure 3: recall by object size, on the delegate each model uses by default.
    fig, ax = plt.subplots(figsize=(6.3, 3.2))
    chosen = [det.get((m, "GPU")) or det[(m, "CPU")] for m in models]
    for index, (size, colour) in enumerate(SIZES.items()):
        xs = [row + (index - 1) * 0.27 for row in range(len(chosen))]
        values = [100 * e["recall_by_size"].get(size, 0) for e in chosen]
        ax.bar(xs, values, width=0.25, color=colour, label=size)
        for x, value in zip(xs, values):
            ax.text(x, value + 1, f"{value:.0f}", ha="center", fontsize=8.5, color=INK)
    ax.set_xticks(range(len(chosen)), [f"{NAMES[e['model']].replace('EfficientDet-', '')}\n({e['delegate']})" for e in chosen])
    ax.set_ylabel("Recall at the app's thresholds (%)")
    ax.set_ylim(0, 60)
    style_axes(ax, "y")
    handles, labels = ax.get_legend_handles_labels()
    ax.legend(handles, [f"{label} objects" for label in labels], frameon=False, fontsize=9, ncol=3, loc="lower center", bbox_to_anchor=(0.5, 1.0))
    paths["sizes"] = save(fig, folder / "fig3-recall-by-size.png")

    # Figure 4: open-set face identification against the number of saved people.
    rows = results["faces"]["identification"]["by_saved_people"]
    saved = [int(k) for k in rows]
    fig, axes = plt.subplots(1, 2, figsize=(6.3, 3.0))
    for ax, key, title, limits in ((axes[0], "named_right", "Saved person named correctly (%)", (97, 100)),
                                   (axes[1], "stranger_named", "Stranger given a saved name (%)", (0, 10.5))):
        values = [100 * rows[str(k)][key] for k in saved]
        low = [100 * (rows[str(k)][key] - rows[str(k)][key + "_ci"][0]) for k in saved]
        high = [100 * (rows[str(k)][key + "_ci"][1] - rows[str(k)][key]) for k in saved]
        ax.errorbar(saved, values, yerr=[low, high], color=BLUE, linewidth=2, marker="o", markersize=5, capsize=3, ecolor=SECONDARY, elinewidth=1)
        for k, value, top in zip(saved, values, high):
            if k != 3:  # 1, 3 and 5 sit too close for three labels; Table 5 has every value
                ax.annotate(f"{value:.1f}", (k, value + top), xytext=(0, 4), textcoords="offset points", ha="center", fontsize=8.5, color=INK)
        ax.set_title(title, fontsize=9.5, color=INK)
        ax.set_xlabel("Number of saved people")
        ax.set_xticks(saved)
        ax.set_ylim(*limits)
        style_axes(ax, "y")
    paths["faces"] = save(fig, folder / "fig4-face-identification.png")
    return paths


def save(fig, path: Path) -> Path:
    fig.tight_layout()
    fig.savefig(path, dpi=220, facecolor="white")
    plt.close(fig)
    return path


# ---------- Word helpers ----------

def shade(cell, fill):
    shading = OxmlElement("w:shd")
    shading.set(qn("w:val"), "clear")
    shading.set(qn("w:color"), "auto")
    shading.set(qn("w:fill"), fill)
    cell._tc.get_or_add_tcPr().append(shading)


def table(doc, header, rows, widths, size=9, first_bold=False):
    grid = doc.add_table(rows=1, cols=len(header))
    grid.style = "Table Grid"
    grid.autofit = False
    repeat = OxmlElement("w:tblHeader")
    repeat.set(qn("w:val"), "true")
    grid.rows[0]._tr.get_or_add_trPr().append(repeat)
    for row_index, values in enumerate([header] + rows):
        cells = grid.rows[0].cells if row_index == 0 else grid.add_row().cells
        for column, (cell, value) in enumerate(zip(cells, values)):
            cell.width = Cm(widths[column])
            paragraph = cell.paragraphs[0]
            paragraph.paragraph_format.space_after = Pt(0)
            run = paragraph.add_run(str(value))
            run.font.size = Pt(size)
            run.bold = row_index == 0 or (first_bold and column == 0)
            if row_index == 0:
                shade(cell, "EDEDEA")
    doc.add_paragraph()
    return grid


def caption(doc, label, text):
    paragraph = doc.add_paragraph()
    paragraph.paragraph_format.space_after = Pt(4)
    paragraph.paragraph_format.keep_with_next = True
    paragraph.add_run(label + " ").bold = True
    paragraph.add_run(text)
    for run in paragraph.runs:
        run.font.size = Pt(9.5)


def figure(doc, path, label, text):
    paragraph = doc.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    paragraph.paragraph_format.keep_with_next = True
    paragraph.add_run().add_picture(str(path), width=Cm(15.5))
    caption(doc, label, text)
    doc.add_paragraph()


def bullets(doc, items):
    for item in items:
        doc.add_paragraph(item, style="List Bullet")


def note(doc, text):
    paragraph = doc.add_paragraph()
    run = paragraph.add_run(text)
    run.italic = True
    run.font.color.rgb = RGBColor(0x52, 0x51, 0x4E)


def orient(doc, landscape: bool):
    section = doc.add_section(WD_SECTION.NEW_PAGE)
    wide, narrow = max(section.page_width, section.page_height), min(section.page_width, section.page_height)
    section.orientation = WD_ORIENT.LANDSCAPE if landscape else WD_ORIENT.PORTRAIT
    section.page_width, section.page_height = (wide, narrow) if landscape else (narrow, wide)


FEATURES_HEADER = ["Feature", "Nungil (this work)", "Seeing AI", "Google Lookout", "Be My Eyes", "Envision", "Sullivan+", "TapTapSee"]
FEATURES = [
    ["Platform", "Android 7.0+", "iOS, Android [1,2]", "Android 6+ [4]", "iOS, Android [6,7]", "iOS, Android [10]", "iOS, Android [14,16*]", "iOS, Android [18]"],
    ["Where processing runs", "On the device; only the optional walking-route request uses the network", "Not stated [3]",
     "\"Core experience\" on device (2018) [5]; food labels offline [4]", "Be My AI requires internet [8*]",
     "Instant Text offline [12,13]; other modes not stated", "Not stated", "Cloud: image is uploaded [19]"],
    ["Live object detection", "Yes, 80 COCO classes", "iOS with LiDAR only (World channel) [1]", "Explore mode, beta [4]", "—",
     "Explore [12]", "AI mode; live operation unverified [14]", "No; photo or short video [18,19]"],
    ["Direction of objects", "Compass heading + camera field of view, spoken", "Spatial audio (World) [1]",
     "Direction and distance in Find mode [4]", "—", "Yes [10]", "Spoken location [15]", "—"],
    ["360° room summary", "Yes", "—", "—", "—", "—", "—", "—"],
    ["Colour naming", "Yes, 11 colour names", "Yes [1]", "—", "—", "Yes [10,11]", "Yes [14]", "—"],
    ["Find a named object", "Beeps + vibration; object classes, saved people, saved objects", "Find My Things [1]",
     "Find mode, 7 fixed categories [4]", "—", "Find Objects, in-app list [11]", "Object Finder by voice [15]", "—"],
    ["Recognise saved faces", "Yes", "Yes [1]", "—", "—", "Yes [10,11]", "Unverified [16*]", "—"],
    ["Teach personal objects", "Yes (appearance embeddings)", "Yes, Find My Things [1]; Android unverified", "—", "—", "—", "—", "—"],
    ["Text, QR, barcode", "Latin text, QR, barcode; no Korean OCR", "Text, barcode, QR [1]", "Text, documents, food barcodes [4]",
     "No dedicated mode", "Text, barcode [10]; offline Korean OCR [12]", "Text, PDF [14]; QR [17]", "Barcode, QR [20]"],
    ["Walking hazard warnings", "Walls, steps, stairs, drop-offs (ARCore depth)", "— [3]", "—", "—", "—", "—", "—"],
    ["Navigation to places", "Saved places; online route or offline direction beacon", "Indoor recorded routes, iOS [1]", "—", "—", "—", "—", "—"],
    ["Hands-free voice control", "Wake word; all functions", "Unverified", "Assistant launch; voice questions in English [4]",
     "On Meta glasses [9]", "Voice Mode in Ask Envision [11]", "Unverified", "—"],
    ["Korean", "Interface and speech", "Yes [1,2]", "Yes [4]", "Yes [6]", "Yes [11,12]", "Yes [14,15]", "Not listed [20]"],
    ["Published accuracy or latency", "This work (Tables 3–6)", "None [3]", "None [4]", "None found",
     "Relative only (\"30% faster\") [13]", "None found", "\"7 to 10 seconds\" per photo [19]"],
]
REFERENCES = [
    "Microsoft. Seeing AI, App Store listing. https://apps.apple.com/us/app/seeing-ai/id999062298",
    "Microsoft. Seeing AI app launches on Android (4 Dec 2023). https://blogs.microsoft.com/accessibility/seeing-ai-app-launches-on-android-including-new-and-updated-features-and-new-languages/",
    "Microsoft. Seeing AI User Manual, Rev. D (12 Dec 2024). https://www.microsoft.com/en-us/garage/wp-content/uploads/2025/04/Seeing-AI-User-Manual.pdf",
    "Google. Lookout help. https://support.google.com/accessibility/android/answer/9031274",
    "Google. Lookout announcement (8 May 2018). https://blog.google/company-news/outreach-and-initiatives/accessibility/lookout-app-help-blind-and-visually-impaired-people-learn-about-their-surroundings/",
    "Be My Eyes, App Store listing. https://apps.apple.com/us/app/be-my-eyes/id905177575",
    "Be My Eyes. https://www.bemyeyes.com/",
    "Be My Eyes. Using Be My AI (page not retrieved directly). https://support.bemyeyes.com/hc/en-us/articles/18133134809105-Using-Be-My-AI",
    "Meta. Be My Eyes on AI glasses. https://www.meta.com/help/ai-glasses/1524316621830425/",
    "Envision. Envision app. https://www.letsenvision.com/app",
    "Envision AI, App Store listing. https://apps.apple.com/us/app/envision-ai/id1268632314",
    "Envision. Android release notes. https://www.letsenvision.com/updates/android",
    "Envision. Instant Text (2 Mar 2023). https://www.letsenvision.com/blog/instant-text",
    "TUAT. Sullivan+, App Store listing (US). https://apps.apple.com/us/app/sullivan/id1475001234",
    "TUAT. Sullivan+, App Store listing (KR). https://apps.apple.com/kr/app/%EC%84%A4%EB%A6%AC%EB%B2%88/id1475001234",
    "TUAT. Sullivan+, Google Play listing (page not retrieved directly). https://play.google.com/store/apps/details?id=tuat.kr.sullivan",
    "TUAT. Sullivan+ functions (Sep 2019). https://blog.mysullivan.org/2019/09/sullivan-plus-about-functions.html",
    "TapTapSee. https://taptapseeapp.com/",
    "TapTapSee. Instructions. https://taptapseeapp.com/instruction.html",
    "TapTapSee, App Store listing. https://apps.apple.com/us/app/taptapsee/id567635020",
    "Google. MediaPipe Object Detector guide (model benchmarks on Pixel 6). https://developers.google.com/edge/mediapipe/solutions/vision/object_detector",
    "TensorFlow Blog. Easier object detection on mobile with TensorFlow Lite (16 Jun 2021). https://blog.tensorflow.org/2021/06/easier-object-detection-on-mobile-with-tf-lite.html",
    "F. Schroff, D. Kalenichenko, J. Philbin. FaceNet: A Unified Embedding for Face Recognition and Clustering. CVPR 2015. https://arxiv.org/abs/1503.03832",
    "S. Serengil. DeepFace benchmarks. https://github.com/serengil/deepface/tree/master/benchmarks",
    "T.-Y. Lin et al. Microsoft COCO: Common Objects in Context. ECCV 2014. https://arxiv.org/abs/1405.0312",
    "G. B. Huang, M. Ramesh, T. Berg, E. Learned-Miller. Labeled Faces in the Wild. University of Massachusetts Amherst, Technical Report 07-49, 2007.",
    "M. Tan, R. Pang, Q. V. Le. EfficientDet: Scalable and Efficient Object Detection. CVPR 2020. https://arxiv.org/abs/1911.09070",
]
RESEARCH_HEADER = ["System", "Platform", "Inference", "Functions", "Model(s)", "Evaluation data", "Reported accuracy", "Reported latency (device)", "User study"]
RESEARCH = [
    ["Lin et al., 2017 [28]", "Android phone + PC server with GPU", "Mixed: server online, phone offline mode", "OD, Dir, Dist, Obs", "Faster R-CNN, YOLO",
     "Own campus images + Pascal, 7 classes", "Recognition rate 55% (7 classes), 60% (5 classes)", "Qualitative only", "4 visually impaired (partial sight)"],
    ["Bai et al., 2019 [29]", "RGB-D + IMU glasses + smartphone (Snapdragon 820)", "On-device", "Obs, walkable direction, OD, Nav",
     "MobileNetV2 detector trained on COCO; depth ground segmentation", "Office field test", "Not reported", "114 ms object detection (phone)", "20 visually impaired"],
    ["Joshi et al., 2020 [30]", "Single-board processor (quad-core 1.5 GHz), camera, ultrasonic sensor", "On-device", "OD with count, Obs, banknotes", "YOLOv3",
     "Own dataset, 150 test images per class", "Detection accuracy 95.19%, recognition accuracy 99.69%", "0.3 s per frame without GPU, 0.1 s with GPU", "36 (20 visually impaired, 16 blindfolded)"],
    ["See et al., 2022 [31] (abstract only)", "Smartphone with depth camera", "Not accessible", "Obs, OD, Find, Voice and gestures", "Not accessible", "Not accessible",
     "Objects located \"more than 80% of the time\"", "Not accessible", "Not accessible"],
    ["Kuriakose et al., 2023 [32] (DeepNAVI)", "Android smartphone + bone-conduction headset", "On-device", "OD (20 obstacle types), Dir, Dist, Scene",
     "EfficientDet-Lite4, EfficientNet-Lite4", "Own 20-class obstacle and scene datasets", "Obstacle detection accuracy 87.8%, scene 85%", "Not reported", "Pilot with 1 visually impaired user"],
    ["Zhang et al., 2024 [33]", "Raspberry Pi 4B + RealSense D435i", "On-device", "Find (object named by voice), Dist, Voice", "Improved YOLOv5",
     "Own 1,835 images, 3 classes", "Precision 91.7%, recall 94.2%, mAP 93.9%", "4.3 ms (hardware not stated)", "Not reported"],
    ["Chen et al., 2024 [34]", "Wearable touch device + cloud server (A100 GPU)", "Cloud", "Scene, object category by touch", "Qwen-VL-Chat + SETR segmentation",
     "POPE, MME, LLaVA-QA90", "POPE accuracy 0.86–0.89", "0.4–4.4 s per action, end to end", "12 visually impaired"],
    ["Gao et al., 2025 [35]", "Custom glasses (FPGA, video + depth) + smartphone", "Mixed: compression on glasses, detection on phone", "Obs (audio + tactile)",
     "YOLOv5-based cross-modal detector", "720 indoor and outdoor trials", "100% collision avoidance rate", "< 320 ms end to end", "20 with impaired vision"],
    ["Mekonnen et al., 2025 [36]", "Raspberry Pi 5", "On-device", "OD, OCR, Face (enrolment), Voice", "YOLOv8n, Tesseract, face_recognition",
     "Own controlled tests", "OD precision 85.2%, recall 82.7%; face 81.3%; OCR words 89.7%", "OD 0.80 s per frame; face 0.89 s; OCR 1.17 s", "None"],
    ["Yuan et al., 2025 [37] (WalkVLM)", "Vision-language model on cloud devices", "Cloud", "Walking reminders, scene questions", "MiniCPM-V2.6 + LoRA",
     "Walking Awareness Dataset, 1.5k test samples", "Text-similarity scores (TF-IDF 0.166)", "Not reported", "34 surveys; closed-loop test with 3 visually impaired people"],
    ["Alhammad et al., 2026 [38] (Munir)", "ESP32-S3 smart glasses + phone app (Helio G91-Ultra)", "Mixed: face on-device; other functions in the cloud",
     "Face (enrolment), OCR, Scene, OD, Voice", "InsightFace buffalo_l; cloud vision and language APIs", "SCface (1,690 probes) + 30 LFW impostor probes",
     "Face recognition rate 96.69%, false accept rate 0%", "Face 44.3 ms on phone; 3.3 s end to end", "None"],
    ["Lin et al., 2026 [39]", "Head-mounted RealSense D455 + laptop", "Mixed: local perception, remote language model", "Nav (indoor), OD (open vocabulary), Obs, Scene, Voice",
     "RTAB-Map, YOLO-World, GPT-4o-mini", "RePOPE; indoor trials", "RePOPE accuracy 87.9–90.9%", "3.3 s mean guidance response", "None"],
]
RESEARCH_REFERENCES = [
    "B.-S. Lin, C.-C. Lee, P.-Y. Chiang. Simple Smartphone-Based Guiding System for Visually Impaired People. Sensors 2017, 17(6), 1371. https://doi.org/10.3390/s17061371",
    "J. Bai, Z. Liu, Y. Lin, Y. Li, S. Lian, D. Liu. Wearable Travel Aid for Environment Perception and Navigation of Visually Impaired People. Electronics 2019, 8(6), 697. https://doi.org/10.3390/electronics8060697",
    "R. C. Joshi, S. Yadav, M. K. Dutta, C. M. Travieso-Gonzalez. Efficient Multi-Object Detection and Smart Navigation Using Artificial Intelligence for Visually Impaired People. Entropy 2020, 22(9), 941. https://doi.org/10.3390/e22090941",
    "A. R. See, B. G. Sasing, W. D. Advincula. A Smartphone-Based Mobility Assistant Using Depth Imaging for Visually Impaired and Blind. Applied Sciences 2022, 12(6), 2802. https://doi.org/10.3390/app12062802",
    "B. Kuriakose, R. Shrestha, F. E. Sandnes. DeepNAVI: A deep learning based smartphone navigation assistant for people with visual impairments. Expert Systems with Applications 2023, 212, 118720. https://doi.org/10.1016/j.eswa.2022.118720",
    "K. Zhang, Y. Wang, S. Shi, Q. Wang, C. Wang, S. Liu. Improved yolov5 algorithm combined with depth camera and embedded system for blind indoor visual assistance. Scientific Reports 2024, 14, 23000. https://doi.org/10.1038/s41598-024-74416-2",
    "Z. Chen, Z. Liu, K. Wang, K. Wang, S. Lian. A Large Vision-Language Model based Environment Perception System for Visually Impaired People. IEEE/RSJ IROS 2024, pp. 221–228. https://doi.org/10.1109/IROS58592.2024.10801813",
    "Y. Gao, D. Wu, J. Song, X. Zhang, B. Hou, H. Liu, J. Liao, L. Zhou. A wearable obstacle avoidance device for visually impaired individuals with cross-modal learning. Nature Communications 2025, 16, 2857. https://doi.org/10.1038/s41467-025-58085-x",
    "F. Y. Mekonnen, M. F. Al Bataineh, D. Abu Abdoun, A. Serag, K. T. Tamiru, W. Abula, S. Darota. Development of a Fully Autonomous Offline Assistive System for Visually Impaired Individuals: A Privacy-First Approach. Sensors 2025, 25(19), 6006. https://doi.org/10.3390/s25196006",
    "Z. Yuan, T. Zhang, Y. Zhu, J. Zhang, Y. Deng, Z. Jia, P. Luo, X. Duan, J. Zhou, J. Zhang. WalkVLM: Aid Visually Impaired People Walking by Vision Language Model. IEEE/CVF ICCV 2025, pp. 9845–9854. https://arxiv.org/abs/2412.20903",
    "N. Alhammad, A. Alsubaie, R. Alomair, F. Alamro, M. Alammar. Munir: A Multimodal Smart-Glasses System for Enhancing Human–Computer Interaction for Visually Impaired Individuals. Sensors 2026, 26(12), 3950. https://doi.org/10.3390/s26123950",
    "H.-Y. Lin, Y.-H. Fan, C.-C. Chang. Multimodal Navigation System for Visually Impaired Users Using Environmental Perception and Vision-Language Models. Sensors 2026, 26(10), 3045. https://doi.org/10.3390/s26103045",
]
TO_VERIFY = [
    "[31] was read as abstract only (the publisher page could not be opened): phone model, detector, latency and participants are unknown, not absent.",
    "[29] and [34] were read as arXiv preprints; the published versions may differ.",
    "[34]: the POPE accuracy row was reconstructed from a flattened table; check the range 0.86–0.89 against the PDF.",
    "[37]: the author list follows the ICCV page (10 authors); the numbers were read from arXiv version 4, not the camera-ready paper.",
    "[38]: the exact name of the cloud language model was cut off in extraction; LFW is used there only for 30 impostor probes, not for verification accuracy.",
    "[33]: the hardware behind the 4.3 ms figure is not stated in the paper; no Raspberry Pi timing was found.",
    "[30]: the paper calls its hardware a \"single board DSP processor\"; another paper describes it as a Raspberry Pi.",
    "[35]: the smartphone model was not found in the main text.",
    "References are numbered in the order they were collected ([1]–[27] products, models and datasets; [28]–[39] research systems), not in order of first citation; renumber for the target journal.",
    "Table 2: two product references marked * were not retrieved directly, and several cells are marked unverified.",
]
FIELD_TASKS = [
    ["F1 Name an object", "30", "Correct name spoken within 10 s; object 1 m away; 20 COCO classes and 10 others"],
    ["F2 Name a colour", "22", "Correct colour name; 2 objects for each of 11 colours"],
    ["F3 Find a named object", "20", "Phone centred on the target within 30 s; time to found recorded"],
    ["F4 Saved person named", "20", "Enrolled person named correctly; 5 people, 4 views each"],
    ["F5 Stranger not named", "20", "Stranger receives no saved name; 5 strangers, 4 views each"],
    ["F6 Saved object named", "20", "Enrolled personal object named; 10 objects, 2 views each"],
    ["F7 Read text", "20", "Every word of a printed English line read correctly"],
    ["F8 Wall warning", "20", "Warning given before 1 m while walking towards a wall"],
    ["F9 Step warning", "10", "Step or stairs announced before reaching it"],
    ["F10 Works offline", "6", "Airplane mode: detection, colour, find, face, text, walk"],
]


def build(results, paths, target: Path):
    det = {(e["model"], e["delegate"]): e for e in results["detection"]}
    faces = results["faces"]
    ver, ident = faces["verification"], faces["identification"]["by_saved_people"]
    default = det[("efficientdet-lite2.tflite", "GPU")]
    walk = det[("efficientdet-lite0-int8.tflite", "CPU")]
    device = f"{default['device'].title().replace('Infinix Infinix', 'Infinix')} ({default['soc']}, MediaTek Helio G99, Mali-G57 GPU, Android 15)"
    precisions = [e["precision"] for e in det.values()]

    doc = Document()
    normal = doc.styles["Normal"]
    normal.font.name, normal.font.size = "Calibri", Pt(11)
    for section in doc.sections:
        section.left_margin = section.right_margin = Cm(2.5)

    doc.add_heading("Nungil: an on-device scene assistant for blind and low-vision users", level=0)
    note(doc, "Article base: measured results, comparison tables, figures and draft text, ready to copy into the manuscript. "
              "Every number in Tables 3–6 and Figures 1–4 was measured on the phone named in Section 3 and is generated from results.json. "
              "Text in [square brackets starting with PENDING] marks material that has not been measured yet.")

    doc.add_heading("Draft abstract", level=1)
    doc.add_paragraph(
        "We present Nungil, an Android application that describes the surroundings of blind and low-vision users entirely on the device: "
        "it names objects with their direction and colour, summarises a full 360° turn, finds a named object with audio and haptic guidance, "
        "recognises saved people and personal objects, reads text and codes, and warns about walls and steps while walking. "
        f"We measured the deployed pipeline on a mid-range phone ({default['soc']}). On a {default['images']}-image sample of COCO val2017 the default detector reaches "
        f"a precision of {pct(default['precision'])} (95% CI {100 * default['precision_ci'][0]:.1f}–{100 * default['precision_ci'][1]:.1f}) at the application's thresholds, "
        f"with a recall of {pct(default['recall'])} overall and {pct(default['recall_by_size']['large'])} on large objects, at {default['ms_mean']:.0f} ms per image. "
        f"On Labeled Faces in the Wild the face pipeline names a saved person correctly in {pct(ident['10']['named_right'])} of cases with ten saved people, "
        f"while {pct(ident['10']['stranger_named'])} of strangers receive a saved name. "
        "None of twelve comparable published systems that we reviewed reports results on these public benchmarks, "
        "and none reports colour naming, a 360° summary or user-taught personal objects."
    )

    doc.add_heading("Key measured findings", level=1)
    bullets(doc, [
        f"What the application announces is almost always right: precision is {pct(min(precisions))}–{pct(max(precisions))} across all seven model and delegate configurations (Table 3).",
        f"The detector is deliberately conservative: recall is {pct(default['recall'])} over all COCO objects and {pct(default['recall_by_size']['large'])} on large objects for the default model; small objects are almost never reported (Figure 3).",
        f"The default model (EfficientDet-Lite2, GPU) needs {default['ms_mean']:.0f} ms per image ({1000 / default['ms_mean']:.1f} frames per second); the walking model (EfficientDet-Lite0 int8, CPU) needs {walk['ms_mean']:.0f} ms ({1000 / walk['ms_mean']:.1f} frames per second) (Figure 1).",
        f"The GPU makes the default model {det[('efficientdet-lite2.tflite', 'CPU')]['ms_mean'] / default['ms_mean']:.1f} times faster than the CPU with the same accuracy (Figure 2).",
        f"Face verification on LFW: {100 * ver['ten_fold_accuracy']:.2f}% ± {100 * ver['ten_fold_sd']:.2f} (standard 10-fold protocol); at the application's threshold the true accept rate is {pct(ver['true_accept'])} and the false accept rate {pct(ver['false_accept'])} (Table 4).",
        f"Saved people are named correctly in {pct(ident['20']['named_right'])}–{pct(ident['1']['named_right'])} of cases and are practically never confused with each other, but the share of strangers who receive a saved name grows with the number of saved people, from {pct(ident['1']['stranger_named'])} with one to {pct(ident['20']['stranger_named'])} with twenty (Table 5, Figure 4).",
    ])

    doc.add_heading("1. System overview", level=1)
    doc.add_paragraph(
        "Nungil runs on Android 7.0 and later. The user holds the phone upright and turns around or walks; the application speaks in English or Korean and is "
        "controlled by voice after a wake word. All recognition runs on the phone. The only network request is an optional walking-route query, "
        "which falls back to an offline direction beacon."
    )
    caption(doc, "Table A.", "Components of the pipeline and the operating points that were evaluated.")
    table(doc, ["Function", "Method", "Operating point"], [
        ["Object detection", "MediaPipe ObjectDetector; EfficientDet-Lite2 (default), EfficientDet-Lite0, SSD MobileNet V2; EfficientDet-Lite0 int8 in walk mode",
         "Detector score ≥ 0.3, at most 10 results per frame; announced at ≥ 0.7 (people ≥ 0.5)"],
        ["Direction", "Rotation-vector heading + box position × camera field of view", "4 or 8 sectors"],
        ["De-duplication", "Clusters by label and angle (20°); count = most seen in one frame", "Confirmed after 3 frames"],
        ["Colour", "Gray-world white balance, per-pixel HSV vote over 24×24 samples", "11 colour names; no colour in dark frames"],
        ["Saved people", "ML Kit face detection, levelled crop, FaceNet-512 (normal + mirrored)", "Cosine ≥ 0.5, margin ≥ 0.08 over runner-up, mean of 3 best samples"],
        ["Saved objects", "MobileNetV3-small image embeddings", "Cosine ≥ 0.75"],
        ["Reader", "ML Kit text recognition and barcode scanning", "Latin script, QR, barcodes"],
        ["Walk mode", "ARCore depth: walls, steps, stairs, drop-offs; detector for people and vehicles", "Analysis every 150 ms"],
    ], [3.0, 7.5, 5.5], first_bold=True)

    orient(doc, landscape=True)
    doc.add_heading("2. Comparison with published research systems", level=1)
    doc.add_paragraph(
        "Table 1 compares Nungil with twelve published assistive systems for blind and visually impaired people (2017–2026). Every cell for the other systems is taken "
        "from the cited paper; \"not reported\" means the paper does not state it, \"not accessible\" means the text could not be read. "
        "Functions: OD = object detection, Dir = direction, Dist = distance, Find = find-object guidance, Face = face recognition, OCR = text reading, "
        "Obs = obstacle or hazard warning, Nav = navigation, Scene = scene description, Voice = voice control."
    )
    caption(doc, "Table 1.", "Nungil and twelve published assistive systems: platform, functions and the results each paper reports.")
    ours = [
        "Nungil (this work)", f"Android phone alone ({default['device'].split()[-2]} {default['device'].split()[-1]}, Helio G99)", "On-device",
        "OD, Dir, colour, 360° summary, Find, Face (enrolment), personal objects, OCR, Obs, Nav, Voice",
        "EfficientDet-Lite0/Lite2, SSD MobileNet V2; FaceNet-512; MobileNetV3-small",
        f"COCO val2017 sample ({default['images']} images); LFW ({faces['images']} images)",
        f"COCO AP {100 * default['ap']:.1f}; precision {pct(default['precision'])}, recall {pct(default['recall'])} at deployed thresholds; LFW verification {100 * ver['ten_fold_accuracy']:.2f}%",
        f"{default['ms_mean']:.0f} ms (Lite2, GPU); {walk['ms_mean']:.0f} ms (Lite0 int8, CPU); face {faces['find_ms_median']:.0f} + {faces['embed_ms_median']:.0f} ms",
        "None yet",
    ]
    table(doc, RESEARCH_HEADER, [ours] + RESEARCH, [2.8, 3.0, 1.9, 3.1, 2.7, 2.7, 3.4, 2.6, 2.4], size=7.5, first_bold=True)
    orient(doc, landscape=False)

    doc.add_heading("2.1 What the comparison shows", level=2)
    bullets(doc, [
        "No shared benchmark. None of the twelve papers reports COCO AP for its detector or LFW verification accuracy; their figures are on their own datasets, "
        "on hallucination benchmarks or on task success. The systems therefore cannot be ranked by accuracy, and this work adds measurements on public benchmarks, "
        "at the deployed settings, with confidence intervals.",
        f"Reported figures such as 95.19% detection accuracy [30] or 93.9% mAP [33] are for a few custom classes and cannot be set against {100 * default['ap']:.1f} COCO AP over 80 classes.",
        "Functions. None of the twelve reports colour naming, a summary of a full 360° turn or teaching personal objects; face enrolment is reported only in [36] and [38].",
        "Hardware. Nungil needs only a phone. Among the twelve, two use a phone without other computing hardware: DeepNAVI [32], which runs on the device with a headset for audio, "
        "and the depth-camera phone of [31], for which we could not establish where inference runs. The others add glasses, a separate depth camera, "
        "a single-board computer, a server or a cloud model.",
        f"Speed. Two papers give on-phone times for a comparable model class: 114 ms for a MobileNetV2 detector on a Snapdragon 820 [29] and 44.3 ms for face embedding on a Helio G91-Ultra [38]. "
        f"We measured {det[('ssd-mobilenet-v2.tflite', 'GPU')]['ms_mean']:.0f}–{default['ms_mean']:.0f} ms for detection and {faces['find_ms_median']:.0f} + {faces['embed_ms_median']:.0f} ms per face. "
        "Devices and models differ, so these are indicative only.",
        "Where earlier work is ahead: several systems were tested with visually impaired participants ([28], [29], [30], [34], [35]), which this work has not done; "
        "the vision-language systems ([34], [37], [39]) answer open questions about a scene, which a fixed 80-class detector cannot.",
    ])

    orient(doc, landscape=True)
    doc.add_heading("2.2 Commercial applications (secondary comparison)", level=2)
    doc.add_paragraph(
        "Table 2 lists documented features of six assistive applications. Cells come only from official product pages, store listings, help pages and manuals, "
        "each with its reference. A dash means the feature is not listed on the pages reviewed, which is not proof that it is absent. "
        "References marked * could not be retrieved directly and rest on search-result excerpts. The Nungil column describes the implemented application."
    )
    caption(doc, "Table 2.", "Documented features of Nungil and six assistive applications (public sources, retrieved October 2026).")
    table(doc, FEATURES_HEADER, FEATURES, [2.6, 3.6, 3.0, 3.2, 2.6, 3.2, 3.2, 3.0], size=8, first_bold=True)
    orient(doc, landscape=False)

    doc.add_heading("Documented advantages over the applications", level=3)
    bullets(doc, [
        "A spoken summary of a full 360° turn, with duplicate objects merged, is not listed for any of the six applications.",
        "Depth-based warnings about walls, steps, stairs and drop-offs while walking are not listed for any of them; the Seeing AI manual states that it should not be used where the user could be harmed [3].",
        "Hands-free control of every function through a wake word is not listed for any of them on a phone.",
        "All recognition runs on the device; for the other applications offline operation is documented only for parts of the product, or not at all, and TapTapSee and Be My AI require a connection [8,19].",
        "None of the six applications publishes accuracy figures; this work reports measured precision, recall, latency and face-recognition error rates with confidence intervals.",
    ])
    doc.add_heading("Documented disadvantages", level=3)
    bullets(doc, [
        "No Korean text recognition, which Envision offers offline [12].",
        "A fixed vocabulary of 80 object classes and no generative scene description, which Seeing AI and Be My AI provide.",
        "Android only; walk mode needs a phone that supports ARCore depth.",
        "No evaluation with blind or low-vision users yet.",
    ])

    doc.add_heading("3. Evaluation method", level=1)
    doc.add_paragraph(
        f"Device. All measurements were taken on one phone, an {device}, connected to a charger, with the benchmark in the foreground. "
        "The application was built from commit b65c9b3 of the main branch plus local changes that do not touch the detector or the face pipeline."
    )
    doc.add_paragraph(
        "Procedure. An instrumented test runs the application's own classes on the phone: the detector factory with the shipped model files and settings, "
        "and the face path (face detection, quality gate, levelled crop, embedding). Images are read from storage instead of the camera, so that every "
        "configuration sees exactly the same input. Raw detections, embeddings and per-image times are written on the phone and analysed on a laptop."
    )
    doc.add_paragraph(
        f"Object detection. A fixed random sample of {default['images']} images of COCO val2017 [25] (seed 2026). Each of the four models was run on the GPU and the CPU "
        "where supported (the int8 model runs on the CPU only), seven configurations in total. We report COCO AP@[.5:.95] and AP50 at the deployed settings "
        "(detector score 0.3, at most 10 results per image), and precision and recall at the application's default announcement thresholds (0.7, people 0.5) "
        "with greedy matching at IoU 0.5; detections that fall on COCO crowd regions are ignored. Recall is also given by COCO object size "
        "(small < 32², medium < 96², large ≥ 96² pixels). Latency is the time of the detector call, excluding the first 20 images of each run."
    )
    doc.add_paragraph(
        f"Face recognition. All {faces['images']} images of Labeled Faces in the Wild [26]. The labelled person is taken to be the face nearest the image centre. "
        "Verification uses the standard 6,000 pairs; a face rejected by the application's quality gate counts as a non-match. Open-set identification follows the "
        f"application's use: G saved people are drawn at random from the {results['faces']['identification']['people_eligible']} identities with at least 8 usable images, "
        "5 images of each are enrolled, up to 5 other images of each are genuine probes, and the same number of images of people who are not saved are stranger probes; "
        "300 random trials per G. The application's matching rule (threshold 0.5, margin 0.08, mean of the 3 best samples) was re-implemented for the analysis."
    )
    doc.add_paragraph(
        "Statistics. All intervals are 95%. Precision, recall and identification rates use a bootstrap over images or trials (2,000 resamples); "
        "verification rates use the Wilson interval."
    )

    doc.add_heading("4. Results", level=1)
    doc.add_heading("4.1 Object detection", level=2)
    caption(doc, "Table 3.", f"Object detection measured on the {default['device'].split()[-2]} {default['device'].split()[-1]}, {default['images']} COCO val2017 images. "
                             "Precision and recall at the application's thresholds, 95% CI in brackets. Confidence is the mean detector score of true and false positives.")
    rows = []
    for key in ORDER:
        e = det.get(key)
        if e:
            rows.append([NAMES[e["model"]], e["delegate"], f"{100 * e['ap']:.1f}", f"{100 * e['ap50']:.1f}", pct(e["precision"], e["precision_ci"]),
                         pct(e["recall"], e["recall_ci"]), pct(e["recall_by_size"]["large"]), f"{e['confidence_tp']:.2f} / {e['confidence_fp']:.2f}",
                         f"{e['ms_mean']:.0f} / {e['ms_p95']:.0f}", f"{1000 / e['ms_mean']:.1f}"])
    table(doc, ["Model", "Delegate", "AP", "AP50", "Precision", "Recall", "Recall, large", "Confidence TP / FP", "Latency ms, mean / p95", "FPS"],
          rows, [2.4, 1.5, 0.9, 1.0, 2.2, 2.2, 1.2, 1.9, 1.6, 1.1], size=8)
    doc.add_paragraph(
        f"Precision is between {pct(min(precisions))} and {pct(max(precisions))} for every configuration: at the announcement thresholds the application rarely names "
        f"something that is not there. Recall is low by design. The default model finds {pct(default['recall'])} of all annotated objects and "
        f"{pct(default['recall_by_size']['large'])} of the large ones; COCO contains many small and distant objects that a hand-held assistant does not need to announce, "
        f"and the application reports at most 10 objects per frame. True positives have a mean detector score of {default['confidence_tp']:.2f} against "
        f"{default['confidence_fp']:.2f} for false positives."
    )
    figure(doc, paths["latency"], "Figure 1.", "Mean inference time per image for each model on the GPU and the CPU. The int8 model runs on the CPU only.")
    figure(doc, paths["tradeoff"], "Figure 2.", "Accuracy against speed for the seven configurations (SSD = SSD MobileNet V2). The delegate changes the speed but not the accuracy.")
    figure(doc, paths["sizes"], "Figure 3.", "Recall at the application's thresholds by COCO object size, on the delegate each model uses by default.")

    doc.add_heading("4.2 Face recognition", level=2)
    caption(doc, "Table 4.", f"Face pipeline measured on the phone, LFW ({faces['images']} images).")
    table(doc, ["Measure", "Value"], [
        ["Face found", pct(faces["face_found"])],
        ["Face passes the application's quality gate and is embedded", pct(faces["embedded"])],
        ["Verification accuracy at the application's threshold 0.5 (6,000 pairs)", pct(ver["accuracy_at_0.5"])],
        ["True accept rate at 0.5", pct(ver["true_accept"], ver["true_accept_ci"])],
        ["False accept rate at 0.5", pct(ver["false_accept"], ver["false_accept_ci"])],
        ["Verification accuracy, standard 10-fold protocol", f"{100 * ver['ten_fold_accuracy']:.2f}% ± {100 * ver['ten_fold_sd']:.2f}"],
        ["Time per face, median: detection + embedding", f"{faces['find_ms_median']:.0f} + {faces['embed_ms_median']:.0f} ms"],
    ], [10.5, 5.5])
    caption(doc, "Table 5.", "Open-set identification with the application's matching rule, 5 enrolled images per saved person, 300 random trials per row, 95% CI in brackets.")
    table(doc, ["Saved people", "Saved person named correctly", "Saved person given another's name", "Stranger given a saved name", "Genuine / stranger probes"], [
        [g, pct(r["named_right"], r["named_right_ci"]), pct(r["named_wrong"], r["named_wrong_ci"], 2), pct(r["stranger_named"], r["stranger_named_ci"]),
         f"{r['genuine_probes']} / {r['stranger_probes']}"] for g, r in ident.items()
    ], [2.2, 3.8, 3.6, 3.6, 2.8])
    doc.add_paragraph(
        f"A saved person is named correctly in {pct(ident['10']['named_right'])} of cases with ten saved people, and saved people are almost never given each other's names. "
        f"The weak point is strangers: the share who receive a saved name rises from {pct(ident['1']['stranger_named'])} with one saved person to "
        f"{pct(ident['10']['stranger_named'])} with ten and {pct(ident['20']['stranger_named'])} with twenty, because every additional saved person is one more chance of a false match. "
        "These figures use five enrolled images per person taken from LFW; the application enrols more samples over five head poses, which was not reproduced here."
    )
    figure(doc, paths["faces"], "Figure 4.", "Open-set face identification against the number of saved people; bars are 95% confidence intervals. The two panels have different vertical scales, and the left one does not start at zero.")

    doc.add_heading("4.3 Comparison with published model figures", level=2)
    doc.add_paragraph(
        "Table 6 sets our measurements beside the figures published for the same models. They are not directly comparable: the published accuracy is for the whole "
        "of COCO val2017 without the deployed limits on score and number of results, and the published latency is for a Google Pixel 6."
    )
    caption(doc, "Table 6.", "This work against published figures for the same models.")
    rows = []
    for model, (published_ap, published_ms) in PUBLISHED.items():
        gpu, cpu = det.get((model, "GPU")), det.get((model, "CPU"))
        rows.append([NAMES[model], " / ".join(f"{100 * e['ap']:.1f}" if e else "n/a" for e in (gpu, cpu)), published_ap,
                     " / ".join(f"{e['ms_mean']:.0f}" if e else "n/a" for e in (gpu, cpu)), published_ms])
    table(doc, ["Model", "AP, this work (GPU / CPU)", "Published COCO mAP [22]", "Latency ms, this work (GPU / CPU)", "Published latency ms, Pixel 6 (GPU / CPU) [21]"],
          rows, [3.4, 3.0, 3.0, 3.2, 3.4], first_bold=True)
    doc.add_paragraph(
        f"For face verification we measured {100 * ver['ten_fold_accuracy']:.2f}% on LFW; an independent benchmark reports 98.4% for FaceNet-512 [24] and the original "
        "FaceNet paper 99.63% [23]. We did not investigate the cause of the difference; the face detector, the crop and the quality gate differ from those benchmarks."
    )

    doc.add_heading("4.4 Optional: head-to-head field trials with applications", level=2)
    note(doc, "[PENDING, optional: not measured. The main comparison is Table 1. If the trials are run, results go into bench/field-trials.csv and analyze.py produces the table with Wilson intervals and Fisher's exact test against Nungil.]")
    doc.add_paragraph(
        "Nungil, Google Lookout, Envision and Sullivan+ are tested on the same phone, in the same scenes, by the same operator. Seeing AI could not be installed on the test phone. "
        "A task that an application does not offer is recorded as not supported."
    )
    caption(doc, "Table 7.", "Field-trial tasks, trials per application and success criterion.")
    table(doc, ["Task", "Trials", "Success criterion"], FIELD_TASKS, [4.2, 1.6, 10.2], first_bold=True)

    doc.add_heading("5. Limitations and threats to validity", level=1)
    bullets(doc, [
        "One phone and one run per configuration; run-to-run variation and other devices were not measured.",
        "Still images from public datasets, not live camera frames: motion blur, focus, lighting and the multi-frame confirmation of the scan are not covered.",
        "AP is limited by the deployed settings (score ≥ 0.3, at most 10 results), so it is lower than the published model figures.",
        "The identification experiment replays the matching rule in the analysis script with five LFW images per person; enrolment in the application is different.",
        "Colour naming, find-object guidance, saved objects, the reader and walk mode have not been measured (Section 4.4 gives a protocol).",
        "The comparisons in Tables 1 and 2 rest on what the papers and product pages state, not on our own measurement of those systems; no cited paper uses the same benchmark as this work.",
        "No study with blind or low-vision users has been carried out.",
    ])

    doc.add_heading("6. Reproducibility", level=1)
    bullets(doc, [
        "bench/prepare.py downloads the COCO sample (seed 2026) and LFW.",
        "app/src/androidTest/java/com/nungil/bench/DeviceBench.kt runs on the phone (am instrument) and writes the raw results.",
        "bench/analyze.py computes every number in Tables 3–6; bench/make_article.py builds the figures and this file.",
    ])

    doc.add_heading("7. To verify before submission", level=1)
    bullets(doc, TO_VERIFY)

    doc.add_heading("References", level=1)
    for number, reference in enumerate(REFERENCES + RESEARCH_REFERENCES, 1):
        paragraph = doc.add_paragraph(f"[{number}] {reference}")
        paragraph.paragraph_format.space_after = Pt(2)
        for run in paragraph.runs:
            run.font.size = Pt(9)
    doc.save(target)


if __name__ == "__main__":
    data = Path(sys.argv[1] if len(sys.argv) > 1 else "D:/nungil-bench")
    results = json.loads((data / "results.json").read_text())
    target = data / (sys.argv[2] if len(sys.argv) > 2 else "Nungil-article-base.docx")
    build(results, figures(results, data / "figures"), target)
    print("wrote", target)
