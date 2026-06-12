import { useTheme } from '@mui/material';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import NotificationsIcon from '@mui/icons-material/Notifications';
import FlagIcon from '@mui/icons-material/Flag';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import GatewayIcon from '@mui/icons-material/CallSplit';
import TimerIcon from '@mui/icons-material/Timer';

interface PaletteNode {
  type: string;
  label: string;
  icon: React.ReactNode;
  /** MUI theme palette path like "success.main" or raw string */
  colorKey: string;
}

const NODES: PaletteNode[] = [
  { type: 'bpmn:StartEvent', label: 'Start Event', icon: <PlayArrowIcon fontSize="small" />, colorKey: 'success.main' },
  { type: 'bpmn:ServiceTask', label: 'Service Task', icon: <AccountTreeIcon fontSize="small" />, colorKey: 'primary.main' },
  { type: 'ai:AiDecisionTask', label: 'AI Decision', icon: <SmartToyIcon fontSize="small" />, colorKey: 'secondary.main' },
  { type: 'bpmn:ServiceTask:notification', label: 'Notification', icon: <NotificationsIcon fontSize="small" />, colorKey: 'warning.main' },
  { type: 'bpmn:ExclusiveGateway', label: 'Gateway', icon: <GatewayIcon fontSize="small" />, colorKey: 'info.main' },
  { type: 'bpmn:IntermediateCatchEvent', label: 'Timer Event', icon: <TimerIcon fontSize="small" />, colorKey: 'text.secondary' },
  { type: 'bpmn:EndEvent', label: 'End Event', icon: <FlagIcon fontSize="small" />, colorKey: 'error.main' },
];

interface Props {
  onNodeSelect?: (type: string) => void;
}

/**
 * Draggable node palette for the BPMN workflow designer.
 * Shows available node types with HTML5 drag-and-drop support.
 * Uses MUI theme palette tokens instead of raw hex colors (v3.1-04 / GAP-027).
 * DnD wire: uses application/bpmn-node mime type for type-safe drop (M4-SS-03).
 */
export default function NodePalette({ onNodeSelect }: Props) {
  const theme = useTheme();

  /** Resolve a theme path like "success.main" to its actual color value */
  const resolveColor = (colorKey: string): string => {
    const parts = colorKey.split('.');
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    let current: any = theme.palette;
    for (const part of parts) {
      current = current?.[part];
    }
    return typeof current === 'string' ? current : colorKey;
  };

  return (
    <Paper variant="outlined" sx={{ p: 1.5, width: '100%' }}>
      <Typography variant="caption" fontWeight={700} color="text.secondary" gutterBottom display="block">
        Node Palette — drag to canvas
      </Typography>
      <Stack spacing={0.75}>
        {NODES.map((node) => {
          const color = resolveColor(node.colorKey);
          return (
            <Box
              key={node.type}
              draggable
              role="button"
              tabIndex={0}
              aria-label={`Drag to add ${node.label}`}
              onDragStart={(e) => {
                // Set all three keys: nodeType (task spec), application/bpmn-node (WorkflowModeler primary), text/plain (fallback)
                e.dataTransfer.setData('nodeType', node.type);
                e.dataTransfer.setData('application/bpmn-node', node.type);
                e.dataTransfer.setData('text/plain', node.type);
                e.dataTransfer.effectAllowed = 'copy';
              }}
              onClick={() => onNodeSelect?.(node.type)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onNodeSelect?.(node.type);
                }
              }}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                p: 0.75,
                borderRadius: 1,
                cursor: 'grab',
                border: '1px solid transparent',
                '&:hover': {
                  bgcolor: 'action.hover',
                  borderColor: 'divider',
                  transform: 'translateX(2px)',
                  transition: 'transform 0.15s ease, border-color 0.15s ease',
                },
                '&:active': {
                  cursor: 'grabbing',
                  bgcolor: 'action.selected',
                },
              }}
            >
              <Box
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  border: `1px solid ${color}`,
                  color: color,
                  flexShrink: 0,
                  transition: 'background-color 0.15s ease',
                  '&:hover': {
                    bgcolor: `${color}18`,
                  },
                }}
              >
                {node.icon}
              </Box>
              <Typography variant="body2" fontSize="0.8rem">
                {node.label}
              </Typography>
            </Box>
          );
        })}
      </Stack>
    </Paper>
  );
}
