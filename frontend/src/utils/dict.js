/** 业务字典与展示辅助 */
export const CATEGORIES = {
  VEG_FRUIT: '蔬菜水果', COOKED: '熟食卤味', PASTRY: '糕点', COLD_DISH: '凉菜',
  LIVE_POULTRY: '活禽', MEAT: '鲜肉', DAILY_GOODS: '日用百货', CLOTHING: '服装鞋帽', OTHER: '其他'
}

export const EVENT_STATUS = {
  DRAFT: { text: '筹备中', type: 'info' },
  OPEN: { text: '报名中', type: 'success' },
  CLOSED: { text: '已截止', type: 'warning' },
  LOTTERY_DONE: { text: '已抽签', type: 'primary' },
  IN_PROGRESS: { text: '集市进行中', type: 'success' },
  FINISHED: { text: '已结束', type: 'info' }
}

export const APP_STATUS = {
  PENDING: { text: '待审核', type: 'warning' },
  APPROVED: { text: '已通过(待抽签)', type: 'primary' },
  REJECTED: { text: '已驳回', type: 'danger' },
  WON: { text: '已中签', type: 'success' },
  LOST: { text: '未中签', type: 'info' }
}

export const PAY_STATUS = {
  UNPAID: { text: '未缴', type: 'danger' },
  PAID: { text: '已缴', type: 'success' },
  WAIVED: { text: '已减免', type: 'info' },
  DEDUCTED: { text: '已扣罚', type: 'warning' },
  REFUNDED: { text: '已退还', type: 'info' }
}

export const COMPLAINT_TYPES = {
  DIARRHEA: '腹泻/食品安全', PRICE: '价格问题', SHORT_WEIGHT: '缺斤少两', HYGIENE: '卫生问题',
  QUALITY: '质量问题', BOUNDARY: '摊位边界纠纷', FEE_DISPUTE: '费用争议',
  DEPOSIT_DISPUTE: '保证金扣罚争议', OTHER: '其他'
}

export const COMPLAINT_STATUS = {
  SUBMITTED: { text: '已提交', type: 'warning' },
  PROCESSING: { text: '处理中', type: 'primary' },
  RESOLVED: { text: '已办结', type: 'success' },
  REJECTED: { text: '已驳回', type: 'info' }
}

export const ADJUST_TYPES = {
  RELOCATE: '临时调位', RAIN_RELOCATE: '雨天调位', FEE_WAIVER: '费用减免',
  DEPOSIT_PENALTY: '保证金扣罚', CREDIT_ADJUST: '信用调整', CATEGORY_CHANGE: '品类变更',
  SALES_SUSPEND: '暂停熟食售卖', LOTTERY_BAN: '冻结熟食抽签资格', BOTH: '暂停售卖+冻结抽签'
}

export const ADJUST_SOURCES = {
  RAIN: '雨天', COMPLAINT: '投诉处理', PATROL: '巡查发现', DISPUTE: '争议处理',
  REQUEST: '摊主申请', POLICY: '政策减免', LAB_TEST: '留样送检', OTHER: '其他'
}

export const RESULT_STATUS = {
  ASSIGNED: { text: '已分配', type: 'success' },
  RELOCATED: { text: '已调位', type: 'warning' },
  CANCELLED: { text: '已取消', type: 'info' },
  BLOCKED: { text: '资格受限', type: 'danger' }
}

export const LAB_TEST_STATUS = {
  INITIATED: { text: '已发起待抽样', type: 'warning' },
  SENT: { text: '检测中', type: 'primary' },
  QUALIFIED: { text: '检测合格', type: 'success' },
  UNQUALIFIED: { text: '检测不合格', type: 'danger' },
  CANCELLED: { text: '已撤销', type: 'info' }
}

export const BATCH_CONTROL = {
  REMOVE: '立即下架',
  KEEP_LIST: '留存销售名单'
}

export const SELLING_STATUS = {
  ON_SALE: { text: '在售', type: 'danger' },
  REMOVED: { text: '已下架', type: 'success' },
  KEPT_LIST: { text: '已留存名单', type: 'warning' },
  SOLD_OUT: { text: '已售罄', type: 'info' }
}

export const SUSPENSION_TYPE = {
  SALES_SUSPEND: '暂停熟食售卖',
  LOTTERY_BAN: '冻结熟食抽签资格',
  BOTH: '暂停售卖+冻结抽签'
}

export const SUSPENSION_STATUS = {
  ACTIVE: { text: '生效中', type: 'danger' },
  REVOKED: { text: '已解除', type: 'success' },
  EXPIRED: { text: '已到期', type: 'info' }
}

export const SUSPENSION_REASON = {
  INVESTIGATING: '送检调查期间',
  LAB_UNQUALIFIED: '检测不合格',
  MANUAL: '管理员决定'
}

export function catName(code) { return CATEGORIES[code] || code || '-' }

export function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-' }
