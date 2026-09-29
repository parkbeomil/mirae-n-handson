# item-bank-php 비즈니스 규칙 교차검증용 추출본

- 작성일: 2026-09-29
- 방법: `legacy/item-bank-php` 코드만 읽고 규칙을 뽑았다. `docs/` 는 읽지 않았고, `vendor/` 는 열지 않았다.
- 범위: 문항 검색 조건 조합(단원 · 난이도 · 태그), 등록 시 검증, 목록의 기본 정렬 · 제외 조건.
- 읽은 파일: `legacy/item-bank-php/search.php`, `legacy/item-bank-php/register.php`, `legacy/item-bank-php/units.php`, `legacy/item-bank-php/inc/db.php`
- 원칙: 코드에 없는 규칙은 쓰지 않는다. 주석과 코드가 다르면 코드를 기준으로 쓴다.

## 요약

| 구분 | 규칙 ID |
|------|---------|
| 검색 — 키워드 | CX-01 ~ CX-03 |
| 검색 — 단원 | CX-04 ~ CX-05 |
| 검색 — 난이도 | CX-06 ~ CX-08 |
| 검색 — 태그 | CX-09 ~ CX-10 |
| 검색 — 조합 · 대상 | CX-11 ~ CX-12 |
| 검색 — 정렬 · 페이징 | CX-13 ~ CX-17 |
| 등록 검증 · 저장 | CX-18 ~ CX-26 |
| 목록 · 선택 목록 | CX-27 ~ CX-29 |

**주석과 코드가 다른 곳 (코드 기준으로 작성함):** CX-06 — 주석은 "1~5 모두 포함"이라 하지만 코드는 `level < 5` 로 난이도 5를 뺀다.

---

## 검색 — 키워드

### CX-01 키워드는 제목과 지문을 부분 일치(LIKE)로 찾는다
- 규칙: 키워드가 비어 있지 않으면 `title` 또는 `stem` 에 키워드가 포함된 문항만 남긴다. 앞뒤 공백은 제거한다.
- 근거: `legacy/item-bank-php/search.php:85`, `legacy/item-bank-php/search.php:97-98`
```php
$q = trim($q);
$like = '%' . $q . '%';
$where .= " AND (title LIKE ? OR stem LIKE ?)";
```

### CX-02 키워드는 100자까지만 쓰고 넘으면 잘라서 경고한다
- 규칙: 100자를 넘는 키워드는 앞 100자로 자르고 경고를 남긴다. 검색은 그대로 진행한다.
- 근거: `legacy/item-bank-php/search.php:86-89`
```php
if (mb_strlen($q, 'UTF-8') > 100) {
    $q = mb_substr($q, 0, 100, 'UTF-8');
    $warnings[] = '키워드가 너무 길어 100자까지만 사용했습니다.';
```

### CX-03 키워드의 `%` · `_` 는 이스케이프하지 않고 와일드카드로 통과시킨다
- 규칙: `%` 나 `_` 가 있어도 그대로 LIKE 패턴에 들어간다. 경고만 표시한다.
- 근거: `legacy/item-bank-php/search.php:94-97`
```php
if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
    $warnings[] = '키워드의 % 와 _ 는 와일드카드로 처리됩니다.';
}
$like = '%' . $q . '%';
```

## 검색 — 단원

### CX-04 단원은 단원 코드와 정확히 일치하는 문항만 남긴다
- 규칙: 단원 값이 비어 있지 않으면 `unit_code = 입력값` 으로 거른다. 앞뒤 공백만 제거하고, 대소문자 변환은 하지 않는다.
- 근거: `legacy/item-bank-php/search.php:109-110`, `legacy/item-bank-php/search.php:118`
```php
$unit = trim($unit);
$where .= " AND unit_code = ?";
```

### CX-05 단원 코드 형식 오류 · 소문자 · 미등록 코드는 경고만 하고 그대로 조회한다
- 규칙: 형식(`^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$`)이 틀리거나 대문자가 아니거나 `unit` 테이블에 없는 코드여도 검색을 막지 않는다. 경고만 남기고 입력값 그대로 조회한다.
- 근거: `legacy/item-bank-php/search.php:111-117`, `legacy/item-bank-php/search.php:141-142`
```php
// 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
$warnings[] = '단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)';
$warnings[] = '등록되지 않은 단원 코드입니다: ' . $unit;
```

## 검색 — 난이도

### CX-06 난이도를 지정하지 않으면 난이도 5 문항은 결과에서 빠진다
- 규칙: 난이도 값이 비어 있으면 `level < 5` 조건이 붙는다. 난이도 5 문항은 `level=5` 를 명시해야만 나온다. 건수 조회(`count_sql`)도 같은 `where` 를 쓰므로 총 건수에서도 빠진다.
- 주석과의 불일치: 바로 위 주석은 "전체 난이도 검색 (1~5 모두 포함)"이지만 코드는 5를 제외한다. 코드 기준으로 적는다.
- 근거: `legacy/item-bank-php/search.php:213-216`, `legacy/item-bank-php/search.php:526`
```php
// 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
if ($level == '') {
    $where .= " AND level < 5";
```

### CX-07 난이도가 1~5 한 자리 숫자면 그 값과 정확히 일치하는 문항만 남긴다
- 규칙: `^[1-5]$` 에 맞으면 `level = 값` 으로 거른다. 앞뒤 공백은 먼저 제거한다.
- 근거: `legacy/item-bank-php/search.php:211`, `legacy/item-bank-php/search.php:217-220`
```php
$level = trim($level);
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
```

### CX-08 난이도가 1~5 밖이면 경고를 남기고 정수로 바꿔 그대로 비교한다
- 규칙: 형식이 맞지 않는 값은 검색을 막지 않고 `(int)` 변환한 값으로 `level = ?` 비교한다. 숫자가 아닌 문자열은 0이 된다. `"05"` 처럼 정규식을 통과 못 하지만 정수로는 1~5가 되는 값은 해당 난이도 결과가 나온다.
- 근거: `legacy/item-bank-php/search.php:223-229`
```php
$warnings[] = '난이도는 1~5 사이여야 합니다.';
$where .= " AND level = ?";
$values[] = (int)$level;
```

## 검색 — 태그

### CX-09 태그는 태그 이름과 정확히 일치하는 문항만 남긴다
- 규칙: 태그 값이 비어 있지 않으면 `item_tag` · `tag` 를 조인한 `EXISTS` 로 `t.name = 입력값` 인 문항만 남긴다. 부분 일치는 없다. 태그는 하나만 받는다.
- 근거: `legacy/item-bank-php/search.php:235-236`, `legacy/item-bank-php/search.php:244-245`
```php
$tag = trim($tag);
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```

### CX-10 태그 이름은 50자로 자르고, 미등록 태그는 경고만 하고 그대로 조회한다
- 규칙: 50자를 넘으면 잘라서 경고한다. `tag` 테이블에 없는 이름이어도 조회는 한다(결과 0건). `%` · `_` 가 있으면 "부분 일치를 지원하지 않는다"는 경고만 낸다.
- 근거: `legacy/item-bank-php/search.php:240-243`, `legacy/item-bank-php/search.php:250`, `legacy/item-bank-php/search.php:258-259`
```php
$tag = mb_substr($tag, 0, 50, 'UTF-8');
// 등록된 태그인지 확인 (없어도 조회는 그대로 한다 → 0건)
$warnings[] = '등록되지 않은 태그입니다: ' . $tag;
```

## 검색 — 조건 조합 · 대상

### CX-11 지정한 조건은 모두 AND 로 결합하고, 같은 이름의 파라미터가 여러 개면 첫 값만 쓴다
- 규칙: `WHERE 1=1` 에 키워드 · 단원 · 난이도 · 태그 조건이 각각 `AND` 로 붙는다. 비어 있는 키워드 · 단원 · 태그는 조건을 붙이지 않는다(난이도만 CX-06 의 기본 조건이 붙는다). `q · unit · level · tag · sort · dir · page` 가 배열로 들어오면 첫 값만 쓴다.
- 근거: `legacy/item-bank-php/search.php:44`, `legacy/item-bank-php/search.php:66-67`
```php
$where    = " WHERE 1=1";
if (isset($params['tag'])) {
    $tag = is_array($params['tag']) ? (string)reset($params['tag']) : (string)$params['tag'];
```

### CX-12 검색은 공개 문항 뷰 `v_item_public` 에서만 조회한다
- 규칙: 목록과 건수 모두 테이블 `item` 이 아니라 뷰 `v_item_public` 을 대상으로 한다. 어떤 문항이 이 뷰에 들어오는지는 뷰 정의에 달려 있고, 이 폴더에는 뷰 정의가 없다(아래 "확인하지 못한 것" 참고).
- 근거: `legacy/item-bank-php/search.php:519-522`, `legacy/item-bank-php/search.php:525-526`
```php
// SQL 조립 — 공개 문항 뷰(v_item_public)를 기준으로 조회한다
$from   = " FROM v_item_public";
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```

## 검색 — 정렬 · 페이징

### CX-13 기본 정렬은 난이도 내림차순, 같으면 번호 오름차순이다
- 규칙: `sort` 를 주지 않으면 `level DESC, id ASC`.
- 근거: `legacy/item-bank-php/search.php:317-320`
```php
case '':
    // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-14 알 수 없는 정렬 기준은 경고하고 기본 정렬로 되돌린다
- 규칙: 허용 값은 `id · title · unit · level · created` 다. 그 밖의 값은 경고 후 `sort` 를 비우고 CX-13 정렬을 쓴다.
- 근거: `legacy/item-bank-php/search.php:322-325`
```php
default:
    $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
    $sort = '';
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-15 정렬 방향은 asc · desc 만 인정하고, 방향을 안 주면 기준별 기본 방향을 쓴다
- 규칙: `dir` 이 `asc` · `desc` 가 아니면 비운다(빈 값이 아니면 경고). 방향이 비었을 때 `id · title · unit` 은 오름차순, `level · created` 는 내림차순이다. `level` · `created` 는 `asc` 일 때만 오름차순이고, `id` · `title` · `unit` 은 `desc` 일 때만 내림차순이다.
- 근거: `legacy/item-bank-php/search.php:268-272`, `legacy/item-bank-php/search.php:301-306`, `legacy/item-bank-php/search.php:328-330`
```php
if ($dir !== 'asc' && $dir !== 'desc') {
case 'level':
    if ($dir === 'asc') {
$dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
```

### CX-16 정렬 기준마다 보조 정렬이 붙고, 단원 정렬은 난이도를 항상 내림차순으로 둔다
- 규칙: `title · level · created` 는 보조로 `id ASC` 를 쓴다. `unit` 은 `unit_code` 뒤에 `level DESC, id ASC` 를 붙이며, `dir` 이 `asc` 여도 `level` 은 내림차순이다. `id` 정렬에는 보조 정렬이 없다.
- 근거: `legacy/item-bank-php/search.php:287-289`, `legacy/item-bank-php/search.php:295-297`
```php
$orderBy = " ORDER BY title DESC, id ASC";
$orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
$orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
```

### CX-17 한 페이지 20건이고, 페이지 번호는 1~999 로 보정한다
- 규칙: `LIMIT 20 OFFSET (page-1)*20`. 숫자가 아닌 값(`''` · `'1'` 제외)은 경고 후 1페이지, 0 이하는 1페이지(경고 없음), 999 초과는 999 로 자르고 경고한다.
- 근거: `legacy/item-bank-php/search.php:337-349`, `legacy/item-bank-php/search.php:523`
```php
if ($page > 999) {
    $page = 999;
    $warnings[] = '페이지 번호는 999 를 넘을 수 없습니다.';
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
```

## 등록 — 검증 · 저장

### CX-18 제목은 앞뒤 공백 제거 후 5자 이상 200자 이하다
- 규칙: 공백만 제거하고 글자 수를 센다(중간 공백은 글자로 센다). 5자 미만 · 200자 초과는 각각 오류다.
- 근거: `legacy/item-bank-php/register.php:41`, `legacy/item-bank-php/register.php:49-54`
```php
$title  = isset($_POST['title'])  ? trim((string)$_POST['title'])  : '';
if (mb_strlen($title, 'UTF-8') < 5) {
if (mb_strlen($title, 'UTF-8') > 200) {
```

### CX-19 지문은 필수다
- 규칙: 앞뒤 공백을 제거한 지문이 빈 문자열이면 오류다. 길이 상한은 코드에 없다.
- 근거: `legacy/item-bank-php/register.php:42`, `legacy/item-bank-php/register.php:56-57`
```php
$stem   = isset($_POST['stem'])   ? trim((string)$_POST['stem'])   : '';
if ($stem === '') {
    $errors[] = '지문을 입력해야 합니다.';
```

### CX-20 단원은 필수이며 `unit` 테이블에 있는 id 여야 한다
- 규칙: 폼이 보내는 `unit_id` 가 조회한 단원 목록의 `id` 중 하나와 문자열로 같아야 한다. 아니면 오류다(단원 코드가 아니라 id 로 검증한다).
- 근거: `legacy/item-bank-php/register.php:27`, `legacy/item-bank-php/register.php:61-67`
```php
$res = $conn->query("SELECT id, code, name FROM unit ORDER BY grade ASC, code ASC");
if ((string)$u['id'] === $unitId) {
    $unitOk = true;
```

### CX-21 난이도는 필수이며 1~5 한 자리 숫자여야 한다
- 규칙: `^[1-5]$` 가 아니면 오류다. 빈 값도 오류다. (검색과 달리 범위 밖 값을 통과시키지 않는다.)
- 근거: `legacy/item-bank-php/register.php:70-71`
```php
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
```

### CX-22 태그는 선택 항목이고, 태그 목록에 없는 id 는 오류 없이 조용히 버린다
- 규칙: 태그를 하나도 안 골라도 등록된다. 제출한 태그 id 중 `tag` 테이블에 있는 것만 저장하고, 없는 id 는 오류를 내지 않고 제외한다.
- 근거: `legacy/item-bank-php/register.php:74-80`, `legacy/item-bank-php/register.php:104`
```php
if ((string)$t['id'] === (string)$tid) {
    $validTagIds[] = (int)$tid;
if (!empty($validTagIds)) {
```

### CX-23 검증 오류는 한꺼번에 모아 보여 주고, 오류가 하나도 없을 때만 저장한다
- 규칙: 제목 · 지문 · 단원 · 난이도 검증은 앞에서 실패해도 멈추지 않고 모두 실행해 오류를 쌓는다. `$errors` 가 비어 있을 때만 저장 단계로 간다.
- 근거: `legacy/item-bank-php/register.php:49-50`, `legacy/item-bank-php/register.php:83`
```php
if (mb_strlen($title, 'UTF-8') < 5) {
    $errors[] = '제목은 5자 이상 입력해야 합니다.';
if (empty($errors)) {
```

### CX-24 새로 등록한 문항은 검수중(`R`) 상태로 저장된다
- 규칙: INSERT 의 `status` 가 `'R'` 로 고정이다. 등록 성공 안내문은 "검수 완료 후 검색에 노출"이라고 안내한다. 검색 화면에서 실제로 빠지는지는 `v_item_public` 정의에 달려 있어 이 폴더의 코드만으로는 확인되지 않는다(CX-12).
- 근거: `legacy/item-bank-php/register.php:93-94`, `legacy/item-bank-php/register.php:116`
```php
"INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
```

### CX-25 문항 id 는 애플리케이션이 `MAX(id)+1` 로 채번한다
- 규칙: 트랜잭션 안에서 `item` 을 `FOR UPDATE` 로 잠그고 `COALESCE(MAX(id), 0) + 1` 을 새 id 로 쓴다.
- 근거: `legacy/item-bank-php/register.php:87-90`
```php
$res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
$newId = (int)$row['next_id'];
```

### CX-26 문항과 태그 저장은 한 트랜잭션이며, 하나라도 실패하면 전부 취소한다
- 규칙: 문항 INSERT 와 태그 INSERT 가 모두 성공해야 `commit` 한다. 예외가 나면 `rollback` 하고 사용자에게는 "저장 중 오류" 로만 알린다.
- 근거: `legacy/item-bank-php/register.php:85`, `legacy/item-bank-php/register.php:114`, `legacy/item-bank-php/register.php:119-122`
```php
$conn->begin_transaction();
$conn->commit();
} catch (Exception $e) {
    $conn->rollback();
```

## 목록 · 선택 목록

### CX-27 단원 목록의 기본 정렬은 학년 오름차순, 같으면 단원 코드 오름차순이다
- 규칙: `ORDER BY u.grade ASC, u.code ASC`. 정렬 옵션은 없다.
- 근거: `legacy/item-bank-php/units.php:19`
```php
ORDER BY u.grade ASC, u.code ASC";
```

### CX-28 단원 목록의 문항 수는 공개(`status = 'A'`) 문항만 센다
- 규칙: 검수중 · 삭제 등 `A` 가 아닌 상태는 문항 수에서 제외한다. 문항이 없는 단원도 목록에는 나온다(0으로 표시).
- 근거: `legacy/item-bank-php/units.php:15-17`
```php
// 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
```

### CX-29 검색 · 등록 화면의 선택 목록 정렬은 단원 = 학년 → 코드, 태그 = id 오름차순이다
- 규칙: 두 화면 모두 단원은 `grade ASC, code ASC`, 태그는 `id ASC` 로 불러온다. 난이도 선택지는 1~5 고정이다.
- 근거: `legacy/item-bank-php/search.php:156`, `legacy/item-bank-php/search.php:178`, `legacy/item-bank-php/register.php:34`
```php
$res = $conn->query("SELECT code, name, grade FROM unit ORDER BY grade ASC, code ASC");
$res = $conn->query("SELECT name FROM tag ORDER BY id ASC");
```

---

## 확인하지 못한 것 (규칙으로 쓰지 않음)

- **`v_item_public` 뷰 정의**: 검색이 어떤 문항을 제외하는지(예: `status = 'A'` 만 포함하는지, 삭제 문항 처리)는 뷰 정의에 달려 있다. 이 폴더에는 정의가 없어 규칙으로 쓰지 않았다. 참고로 `legacy/item-bank-php/units.php:17` 은 `status = 'A'` 를, `legacy/item-bank-php/register.php:84` 의 주석은 검수중(`R`) 문항이 검색에 나오지 않는다고 적고 있으나, 검색 쿼리 자체에는 `status` 조건이 없다.
- **문항 수정 시 검증**: `legacy/item-bank-php/` 에는 수정(update) 화면 · 코드가 없다. `index.php` 와 `inc/layout.php` 는 범위 밖(화면 골격)이라 읽지 않았다.
- **DB 스키마 제약**: `item_tag` 중복 키, 컬럼 길이, 문자셋 같은 제약은 이 폴더에 없다. 태그 id 가 중복 제출됐을 때(`register.php:75-81` 은 중복을 거르지 않는다) 결과는 스키마에 달려 있어 적지 않았다.
