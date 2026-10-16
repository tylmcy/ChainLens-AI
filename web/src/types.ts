/** 与后端 model 模块对齐的响应类型（字段名与 Java record 一致）。 */

export type Severity = 'HIGH' | 'MEDIUM' | 'LOW';

export interface Evidence {
  chainId: number;
  network: string;
  blockNumber: number;
  transactionHash: string;
  logIndex: number | null;
  rpcMethod: string;
  fetchedAt: string;
  rawJsonRef: { rpcMethod: string; jsonPointer: string };
}

export interface Finding {
  ruleId: string;
  severity: Severity;
  confidence: number;
  title: string;
  description: string;
  evidence: Evidence;
}

export interface ReportSource {
  type: 'CHAIN' | 'KNOWLEDGE';
  label: string;
  reference: string;
}

export interface AnalysisReport {
  summary: string;
  status: 'SUCCESS' | 'FAILED';
  actions: string[];
  findings: Finding[];
  sources: ReportSource[];
  limitations: string[];
}

/** Java BigInteger 在 JSON 中通常为数字；前端只展示这些字段，不参与金额计算。 */
export type RpcInteger = number | string;

export interface TxFacts {
  network: string;
  transaction: {
    hash: string;
    from: string;
    to: string | null;
    value: RpcInteger;
    input: string;
    blockNumber: RpcInteger;
    rawJson: string;
  };
  receipt: {
    status: string;
    gasUsed: RpcInteger;
    logs: unknown[];
    rawJson: string;
  };
  block: { number: RpcInteger; timestamp: number; rawJson: string };
  gasCostWei: RpcInteger;
  valueWei: RpcInteger;
  fromNetWei: RpcInteger;
  toNetWei: RpcInteger;
  transfers: Array<{ tokenAddress: string; from: string; to: string; valueWei: RpcInteger; logIndex: RpcInteger }>;
  approvals: Array<{ tokenAddress: string; owner: string; spender: string; valueWei: RpcInteger; logIndex: RpcInteger }>;
  unknownLogTopics: string[];
  calldata: { hasInput: boolean; selector: string | null; candidates: string[]; resolvedSignature: string | null; status: string };
  contractCode: { address: string | null; available: boolean; hasCode: boolean; codeSize: RpcInteger | null; queriedAtBlock: RpcInteger; unavailableReason: string | null };
  fetchedAt: string;
}

export interface ToolCallStep {
  toolName: string;
  arguments: string;
  startedAt: string;
  durationMs: number;
  success: boolean;
  summary: string;
}

export interface AnalysisResult {
  analysisId: string;
  txHash: string;
  network: string;
  report: AnalysisReport;
  facts: TxFacts;
  findings: Finding[];
  toolSteps: ToolCallStep[];
  llmUsed: boolean;
  degraded: boolean;
  persisted: boolean;
  totalDurationMs: number;
  createdAt: string;
}

/** SSE 事件的 payload（后端 event name → data 结构）。 */
export interface StageEvent {
  stage: string;
  message: string;
}

export interface ToolStartedEvent {
  tool: string;
}

export interface ToolCompletedEvent {
  tool: string;
  success: boolean;
  durationMs: number;
  summary: string;
}

export interface RulesCompletedEvent {
  count: number;
  ruleIds: string[];
}

export interface ErrorResponse {
  code: string;
  message: string;
  retryable: boolean;
  details: Record<string, unknown>;
}
