package org.worldcraft.dominioncraft.nation;

/**
 * Жёсткие (машиночитаемые) параграфы конституции.
 *
 * <p>Каждый ключ — двоичный флаг <code>true/false</code>, который
 * прописывается прямо в текст конституции в формате
 * <pre>(Key: true)</pre>
 *
 * <p>Пример статьи:
 * <pre>
 *     Статья 1  (NATION_LEAVE_RULE: false)
 *     Город не может выйти из нации без одобрения монарха.
 * </pre>
 *
 *
 */
public enum ConstitutionKey {

    /**
     * Может ли город покинуть нацию простым `/nation leave`.
     * <ul>
     *   <li>true  → выход возможен без дополнительных процедур;</li>
     *   <li>false → требуется заявка + одобрения, согласно форме правления.</li>
     * </ul>
     */
    NATION_LEAVE_RULE,

    /**
     * Изменение налоговой ставки % требует голосования совета
     * или общенационального референдума.
     */
    TAX_CHANGE_NEEDS_VOTE,

    /**
     * Монарх должен лично утвердить заявку на выход города.
     * Игнорируется, если форма правления не MONARCHY.
     */
    MONARCH_APPROVAL_REQUIRED,

    /**
     * Общенациональный референдум обязателен для выхода города или
     * изменения ключевых статей.
     */
    REFERENDUM_REQUIRED,

    /**
     * Разрешить ли лицам с правом EDIT_CONSTITUTION редактировать текст.
     * Если false — менять конституцию может только лидер.
     */
    EDIT_CONSTITUTION_ALLOWED
}
