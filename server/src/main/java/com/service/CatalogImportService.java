package com.service;

import com.dto.CatalogDTO.*;
import com.dto.CatalogImportDTO.*;
import com.exception.ResourceNotFoundException;
import com.model.InstitutionLecturer.LecturerType;
import com.repository.TeacherRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Recomputes the entire proposal on commit; the client never supplies trusted row actions. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogImportService {
    private final CatalogService catalogs;
    private final TeacherRepository accounts;
    private final ScheduleWriteLock writeLock;
    private final ObjectMapper json;
    private final LecturerAccountService lecturerAccounts;

    private record AccountState(String id, String role, boolean registered, String username, boolean automatic) {}
    private record Context(InstitutionView school, Map<String, DepartmentView> departments,
                           Map<String, LecturerView> lecturers, Map<String, RoomView> rooms,
                           Map<String, AssignmentView> assignments, Map<String, AccountState> accounts) {}
    private record Prepared(ImportRow row, Object input, Long id, Long lecturerId) {}
    private record Proposal(Preview preview, List<Prepared> prepared) {}

    public Preview preview(String schoolCode, Kind kind, MultipartFile file) throws Exception {
        return prepare(schoolCode, kind, file).preview();
    }

    @Transactional(rollbackFor = Exception.class)
    public CommitResult commit(String schoolCode, Kind kind, MultipartFile file, String snapshot) throws Exception {
        if (snapshot == null || !snapshot.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Cần bản xem trước hợp lệ trước khi lưu danh mục.");
        writeLock.acquire();
        var proposal = prepare(schoolCode, kind, file);
        var preview = proposal.preview();
        if (!preview.snapshot().equals(snapshot)) throw new ObjectOptimisticLockingFailureException("CatalogImport", schoolCode);
        if (preview.invalid() > 0) throw new IllegalArgumentException("File có dòng lỗi; hãy sửa và xem trước lại trước khi lưu.");
        for (var prepared : proposal.prepared()) {
            if (prepared.row().action().equals("UNCHANGED")) continue;
            boolean create = prepared.id() == null;
            switch (kind) {
                case LECTURERS -> {
                    var input = (LecturerInput) prepared.input();
                    if (create) catalogs.createLecturer(preview.schoolCode(), input);
                    else catalogs.updateLecturer(preview.schoolCode(), prepared.id(), input);
                }
                case ROOMS -> {
                    var input = (RoomInput) prepared.input();
                    if (create) catalogs.createRoom(preview.schoolCode(), input);
                    else catalogs.updateRoom(preview.schoolCode(), prepared.id(), input);
                }
                case ASSIGNMENTS -> {
                    var input = (AssignmentInput) prepared.input();
                    if (create) catalogs.createAssignment(preview.schoolCode(), prepared.lecturerId(), input);
                    else catalogs.updateAssignment(preview.schoolCode(), prepared.lecturerId(), prepared.id(), input);
                }
            }
        }
        return new CommitResult("SUCCESS", preview.created(), preview.updated(), preview.unchanged());
    }

    private Proposal prepare(String schoolCode, Kind kind, MultipartFile file) throws Exception {
        var sources = new CatalogWorkbookReader().read(file, kind);
        var context = context(schoolCode, kind, sources);
        if (!context.school().active()) throw new IllegalArgumentException("Trường đã ngừng hoạt động; hãy kích hoạt lại trước khi nhập danh mục.");
        var keys = new HashSet<String>();
        var linkedAccounts = new HashSet<String>();
        var prepared = new ArrayList<Prepared>();
        for (var source : sources) prepared.add(row(kind, source, context, keys, linkedAccounts));
        var rows = prepared.stream().map(Prepared::row).toList();
        int created = count(rows, "CREATE"), updated = count(rows, "UPDATE"), unchanged = count(rows, "UNCHANGED"), invalid = count(rows, "ERROR");
        String state = json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(Map.of("context", context, "rows", rows));
        String snapshot = digest((digest(file.getBytes()) + ":" + kind + ":" + state).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new Proposal(new Preview(context.school().schoolCode(), kind, snapshot, created, updated, unchanged, invalid,
                invalid == 0 && created + updated > 0, rows), prepared);
    }

    private Context context(String schoolCode, Kind kind, List<CatalogWorkbookReader.SourceRow> sources) {
        String code = CatalogService.schoolCode(schoolCode);
        var school = catalogs.listInstitutions().stream().filter(s -> s.schoolCode().equals(code)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Trường không tồn tại."));
        var lecturers = index(catalogs.listLecturers(code), LecturerView::lecturerCode);
        var references = new TreeSet<String>();
        if (kind == Kind.LECTURERS) {
            lecturers.values().stream().map(LecturerView::accountTeacherId).filter(Objects::nonNull).forEach(references::add);
            sources.stream().map(r -> r.values().get("accountTeacherId")).filter(s -> s != null && !s.isBlank() && s.length() <= 50)
                    .map(s -> s.strip().toUpperCase(Locale.ROOT)).forEach(references::add);
            lecturers.values().stream().map(LecturerView::scheduleTeacherId).filter(Objects::nonNull).forEach(references::add);
            for (var row : sources) for (String key : List.of("lecturerCode", "existingScheduleTeacherId")) {
                String value = row.values().get(key); if (value != null && !value.isBlank() && value.length() <= 50) references.add(value.strip().toUpperCase(Locale.ROOT));
            }
        }
        var accountStates = new TreeMap<String, AccountState>();
        references.forEach(id -> accountStates.put(id, new AccountState(id, null, false, null, false)));
        accounts.findAllById(references).forEach(a -> accountStates.put(a.getTeacherId(), new AccountState(a.getTeacherId(), a.getRole(), a.getUsername() != null && !a.getUsername().isBlank(), a.getUsername(), a.isAutoProvisioned())));
        if (kind == Kind.LECTURERS) for (var row : sources) {
            String raw = row.values().get("lecturerCode"); if (raw == null || raw.isBlank() || raw.length() > 50) continue;
            String login = raw.strip().toUpperCase(Locale.ROOT);
            accountStates.put("login:" + login, accounts.findByUsernameIgnoreCase(login).map(a -> new AccountState(a.getTeacherId(), a.getRole(), true, a.getUsername(), a.isAutoProvisioned())).orElse(new AccountState(null, null, false, null, false)));
        }
        return new Context(school, index(catalogs.listDepartments(code), DepartmentView::departmentCode), lecturers,
                index(catalogs.listRooms(code), RoomView::roomCode), kind == Kind.ASSIGNMENTS
                ? index(catalogs.listSchoolAssignments(code), a -> assignmentKey(a.lecturerId(), a.courseCode(), a.cohort())) : Map.of(), accountStates);
    }

    private Prepared row(Kind kind, CatalogWorkbookReader.SourceRow source, Context context, Set<String> keys, Set<String> linkedAccounts) {
        var errors = new ArrayList<>(source.errors());
        var values = new LinkedHashMap<String, Object>(); values.putAll(source.values());
        Map<String, Object> previous = Map.of();
        Object input = null;
        Long id = null, lecturerId = null;
        try {
            var raw = source.values();
            switch (kind) {
                case LECTURERS -> {
                    String code = CatalogService.code(raw.get("lecturerCode"), "Mã GV");
                    var existing = context.lecturers().get(code);
                    id = existing == null ? null : existing.id();
                    if (!keys.add(code)) throw new IllegalArgumentException("Mã GV lặp trong file: " + code);
                    String departmentCode = blank(raw.get("departmentCode")) ? null : CatalogService.code(raw.get("departmentCode"), "Mã khoa");
                    Long departmentId = existing == null ? null : existing.departmentId();
                    if (departmentCode != null) {
                        var department = context.departments().get(departmentCode);
                        if (department == null) throw new IllegalArgumentException("Mã khoa chưa có trong trường: " + departmentCode);
                        departmentId = department.id();
                    }
                    String accountId = blank(raw.get("accountTeacherId")) ? (existing == null ? null : existing.accountTeacherId())
                            : CatalogService.code(raw.get("accountTeacherId"), "Mã tài khoản GV");
                    String declaredIdentity = blank(raw.get("existingScheduleTeacherId")) ? existing == null ? null : existing.scheduleTeacherId() : raw.get("existingScheduleTeacherId").strip();
                    var priorAccount = declaredIdentity == null ? null : context.accounts().get(declaredIdentity);
                    if (accountId == null && priorAccount != null && priorAccount.automatic() && code.equals(priorAccount.username())) accountId = priorAccount.id();
                    if (accountId != null) {
                        var account = context.accounts().get(accountId);
                        if (account == null || !account.registered() || !"TEACHER".equals(account.role()))
                            throw new IllegalArgumentException("Mã tài khoản không phải tài khoản GV đã đăng ký: " + accountId);
                        String targetAccount = accountId;
                        if (!linkedAccounts.add(accountId) || context.lecturers().values().stream().anyMatch(l -> targetAccount.equals(l.accountTeacherId()) && !l.lecturerCode().equalsIgnoreCase(code)))
                            throw new IllegalArgumentException("Tài khoản đã liên kết hồ sơ GV khác trong trường: " + accountId);
                    }
                    String oldScheduleId = existing == null ? null : existing.scheduleTeacherId();
                    String declaredId = blank(raw.get("existingScheduleTeacherId")) ? oldScheduleId : raw.get("existingScheduleTeacherId");
                    if (!blank(declaredId)) {
                        String identity = declaredId.strip();
                        if (!linkedAccounts.add("schedule:" + identity) || context.lecturers().values().stream().anyMatch(l -> identity.equals(l.scheduleTeacherId()) && !l.lecturerCode().equalsIgnoreCase(code)))
                            throw new IllegalArgumentException("Hồ sơ lịch đã liên kết GV khác trong trường: " + identity);
                    }
                    catalogs.validateScheduleIdentity(context.school().schoolCode(), oldScheduleId, existing == null ? null : existing.accountTeacherId(), accountId, declaredId);
                    if (accountId == null && active(raw.get("active"), existing == null || existing.active()))
                        lecturerAccounts.checkAvailable(code, raw.get("fullName"), declaredId);
                    input = new LecturerInput(code, CatalogService.text(raw.get("fullName"), 100, "Họ tên"), departmentId,
                            lecturerType(raw.get("lecturerType"), existing == null ? LecturerType.STAFF : existing.lecturerType()),
                            accountId, active(raw.get("active"), existing == null || existing.active()), existing == null ? null : existing.revision(), declaredId);
                    previous = existing == null ? Map.of() : map(existing);
                    if (existing != null) previous.put("existingScheduleTeacherId", oldScheduleId);
                }
                case ROOMS -> {
                    String code = CatalogService.code(raw.get("roomCode"), "Mã phòng");
                    var existing = context.rooms().get(code);
                    id = existing == null ? null : existing.id();
                    if (!keys.add(code)) throw new IllegalArgumentException("Mã phòng lặp trong file: " + code);
                    int capacity = integer(raw.get("capacity"), 1, 10000, "Sức chứa");
                    String type = CatalogService.text(raw.get("roomType"), 30, "Loại phòng").toUpperCase(Locale.ROOT);
                    type = switch (type) {
                        case "PRACTICE", "LAB_PRACTICE" -> "LAB_PRACTICE";
                        case "THEORY", "THEORY_LARGE", "THEORY_MEDIUM", "THEORY_STANDARD" -> capacity >= 120 ? "THEORY_LARGE" : capacity >= 80 ? "THEORY_MEDIUM" : "THEORY_STANDARD";
                        default -> throw new IllegalArgumentException("Loại phòng phải là THEORY hoặc PRACTICE.");
                    };
                    String name = blank(raw.get("roomName")) ? (existing == null || existing.roomName() == null ? "Phòng " + code : existing.roomName()) : raw.get("roomName");
                    String building = blank(raw.get("building")) ? (existing == null ? null : existing.building()) : CatalogService.text(raw.get("building"), 50, "Tòa nhà");
                    input = new RoomInput(existing == null ? code : existing.roomCode(), CatalogService.text(name, 100, "Tên phòng"), type, capacity, building,
                            active(raw.get("active"), existing == null || existing.active()), existing == null ? null : existing.revision());
                    previous = existing == null ? Map.of() : map(existing);
                }
                case ASSIGNMENTS -> {
                    String code = CatalogService.code(raw.get("lecturerCode"), "Mã GV");
                    var lecturer = context.lecturers().get(code);
                    if (lecturer == null) throw new IllegalArgumentException("Chưa có GV trong danh mục trường: " + code + ". Hãy nhập danh mục GV trước.");
                    lecturerId = lecturer.id();
                    String course = CatalogService.code(raw.get("courseCode"), "Mã môn");
                    String cohort = CatalogService.text(raw.get("cohort"), 50, "Khóa sinh viên").toUpperCase(Locale.ROOT);
                    String key = assignmentKey(lecturerId, course, cohort);
                    if (!keys.add(key)) throw new IllegalArgumentException("Phân công GV–môn–khóa lặp trong file.");
                    var existing = context.assignments().get(key);
                    id = existing == null ? null : existing.id();
                    int theory = integer(raw.get("theoryWeight"), 0, 10000, "Trọng số LT"), practice = integer(raw.get("practiceWeight"), 0, 10000, "Trọng số TH");
                    boolean active = active(raw.get("active"), existing == null || existing.active());
                    if (active && theory + practice == 0) throw new IllegalArgumentException("Phân công đang hoạt động cần ít nhất một trọng số lớn hơn 0.");
                    input = new AssignmentInput(course, CatalogService.text(raw.get("courseName"), 180, "Tên môn"), cohort, theory, practice,
                            active, existing == null ? null : existing.revision());
                    previous = existing == null ? Map.of() : map(existing);
                }
            }
            values = new LinkedHashMap<>(map(input)); values.remove("revision");
            if (kind == Kind.ASSIGNMENTS) values.put("lecturerCode", CatalogService.code(source.values().get("lecturerCode"), "Mã GV"));
        } catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        boolean provisioning = kind == Kind.LECTURERS && input instanceof LecturerInput lecturer && lecturer.accountTeacherId() == null && Boolean.TRUE.equals(lecturer.active());
        String action = !errors.isEmpty() ? "ERROR" : id == null ? "CREATE" : !provisioning && unchanged(map(input), previous) ? "UNCHANGED" : "UPDATE";
        if (provisioning && errors.isEmpty()) { values.put("accountUsername", ((LecturerInput) input).lecturerCode()); values.put("accountAction", "Tự tạo"); }
        return new Prepared(new ImportRow(source.number(), action, values, previous, errors), input, id, lecturerId);
    }

    private Map<String, Object> map(Object value) { return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {}); }
    private static boolean unchanged(Map<String, Object> values, Map<String, Object> previous) {
        return values.entrySet().stream().filter(e -> !e.getKey().equals("revision")).allMatch(e -> {
            Object old = previous.get(e.getKey()), next = e.getValue();
            return old instanceof Number a && next instanceof Number b ? a.longValue() == b.longValue() : Objects.equals(old, next);
        });
    }
    private static int integer(String value, int min, int max, String label) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) { throw new IllegalArgumentException(label + " phải là số nguyên " + min + "–" + max + "."); }
    }
    private static boolean active(String value, boolean fallback) {
        if (blank(value)) return fallback;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "TRUE", "1" -> true;
            case "FALSE", "0" -> false;
            default -> throw new IllegalArgumentException("Trạng thái cần TRUE/FALSE hoặc 1/0.");
        };
    }
    private static LecturerType lecturerType(String value, LecturerType fallback) {
        if (blank(value)) return fallback;
        return switch (CatalogWorkbookReader.normalize(value)) {
            case "staff", "co huu" -> LecturerType.STAFF;
            case "visiting", "thinh giang" -> LecturerType.VISITING;
            default -> throw new IllegalArgumentException("Loại GV cần STAFF (cơ hữu) hoặc VISITING (thỉnh giảng).");
        };
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String assignmentKey(Long lecturer, String course, String cohort) { return lecturer + ":" + course.toUpperCase(Locale.ROOT) + ":" + cohort.toUpperCase(Locale.ROOT); }
    private static <T> Map<String, T> index(List<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toMap(v -> key.apply(v).toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a, TreeMap::new));
    }
    private static int count(List<ImportRow> rows, String action) { return (int) rows.stream().filter(r -> r.action().equals(action)).count(); }
    private static String digest(byte[] bytes) throws java.security.NoSuchAlgorithmException { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
